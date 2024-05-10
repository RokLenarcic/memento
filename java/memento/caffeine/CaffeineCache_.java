package memento.caffeine;

import clojure.lang.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import memento.base.CacheKey;
import memento.base.EntryMeta;
import memento.base.LockoutMap;
import memento.base.Segment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

public class CaffeineCache_ {

    private final BiFunction<Segment, ISeq, CacheKey> keyFn;

    private final SecondaryIndex secIndex;
    private final IFn retFn;

    private final IFn retExFn;

    private final Cache<CacheKey, Object> delegate;

    private final Set<SpecialPromise> loads = ConcurrentHashMap.newKeySet();

    public CaffeineCache_(Caffeine<Object, Object> builder, final IFn keyFn, final IFn retFn, final IFn retExFn, SecondaryIndex secIndex) {
        this.keyFn = keyFn == null ?
                (segment, args) -> new CacheKey(segment.getId(), segment.getKeyFn().invoke(args)) :
                (segment, args) -> new CacheKey(segment.getId(), keyFn.invoke(segment.getKeyFn().invoke(args)));
        this.retFn = retFn;
        this.delegate = builder.build();
        this.secIndex = secIndex;
        this.retExFn = retExFn;
    }

    private void initLoad(SpecialPromise promise) {
        promise.init();
        loads.add(promise);
    }

    public Object cached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        do {
            SpecialPromise p = new SpecialPromise(key);
            // check for ongoing load
            Object cached = delegate.asMap().putIfAbsent(key, p);
            if (cached == null) {
                try {
                    initLoad(p);
                    // calculate value
                    Object result = AFn.applyToHelper(segment.getF(), args);
                    if (retFn != null) {
                        result = retFn.invoke(args, result);
                    }
                    if (!p.deliver(result)) {
                        // loader was abandoned during load
                        delegate.asMap().remove(key, p);
                        continue;
                    }
                    // if valid add to secondary index
                    secIndex.add(key, result);
                    if (result instanceof EntryMeta && ((EntryMeta) result).isNoCache()) {
                        delegate.asMap().remove(key, p);
                    } else {
                        delegate.asMap().replace(key, p, result == null ? EntryMeta.NIL : result);
                    }
                    return EntryMeta.unwrap(result);
                } catch (Throwable t) {
                    delegate.asMap().remove(key, p);
                    p.deliverException(retExFn == null ? t : (Throwable) retExFn.invoke(args, t));
                    throw t;
                } finally {
                    p.finishLoad();
                    loads.remove(p);
                }
            } else {
                // join into ongoing load
                if (cached instanceof SpecialPromise) {
                    SpecialPromise sp = (SpecialPromise) cached;
                    Object ret = sp.join();
                    if (!LockoutMap.awaitLockout(cached) || !sp.isUnviable()) {
                        // if not invalidated, return the value
                        return EntryMeta.unwrap(ret);
                    }
                } else {
                    if (!LockoutMap.awaitLockout(cached)) {
                        // if not invalidated, return the value
                        return EntryMeta.unwrap(cached);
                    }
                }
                // else try to initiate load again
            }
        } while (true);
    }

    public Object ifCached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        Object v = delegate.getIfPresent(key);
        Object absent = EntryMeta.absent;
        if (v == null) {
            return absent;
        } else if (v instanceof SpecialPromise) {
            SpecialPromise p = (SpecialPromise) v;
            Object ret = p.getNow(absent);
            if (ret == absent || LockoutMap.awaitLockout(ret) || p.isUnviable()) {
                return absent;
            } else {
                return EntryMeta.unwrap(ret);
            }
        } else {
            return LockoutMap.awaitLockout(v) ? absent : EntryMeta.unwrap(v);
        }
    }

    public void invalidate(Segment segment) {
        final Iterator<Map.Entry<CacheKey, Object>> iter = delegate.asMap().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<CacheKey, Object> it = iter.next();
            if (it.getKey().getId().equals(segment.getId())) {
                Object v = it.getValue();
                if (v instanceof SpecialPromise) {
                    ((SpecialPromise) v).abandonLoad();
                }
                iter.remove();
            }
        }
    }

    public void invalidate(Segment segment, ISeq args) {
        Object v = delegate.asMap().remove(keyFn.apply(segment, args));
        if (v instanceof SpecialPromise) {
            ((SpecialPromise) v).abandonLoad();
        }
    }

    public void invalidateAll() {
        delegate.invalidateAll();
    }

    public void invalidateIds(Iterable<Object> ids) {
        HashSet<CacheKey> keys = new HashSet<>();
        for (Object id : ids) {
            secIndex.drainKeys(id, keys::add);
        }
        ConcurrentMap<CacheKey, Object> map = delegate.asMap();
        for (CacheKey k : keys) {
            Object removed = map.remove(k);
            if (removed instanceof SpecialPromise) {
                ((SpecialPromise) removed).abandonLoad();
            }
        }
        loads.forEach(row -> row.addInvalidIds(ids));
    }

    public void addEntries(Segment segment, IPersistentMap argsToVals) {
        for (Object o : argsToVals) {
            MapEntry entry = (MapEntry) o;
            CacheKey key = keyFn.apply(segment, RT.seq(entry.getKey()));
            Object val = entry.getValue();
            secIndex.add(key, val);
            delegate.put(key, val == null ? EntryMeta.NIL : val);
        }
    }

    public ConcurrentMap<CacheKey, Object> asMap() {
        return delegate.asMap();
    }

    public CacheStats stats() {
        return delegate.stats();
    }

    public void loadData(Map map) {
        map.forEach((Object k, Object v) -> {
            List<Object> list = (List<Object>) k;
            CacheKey key = new CacheKey(list.get(0), list.get(1));
            secIndex.add(key, v);
            delegate.put(key, v == null ? EntryMeta.NIL : v);
        });
    }

    public static RemovalListener<CacheKey, Object> listener(IFn removalListener) {
        return (k, v, removalCause) -> {
            if (!(v instanceof SpecialPromise)) {
                removalListener.invoke(k.getId(), k.getArgs(), v instanceof EntryMeta ? ((EntryMeta) v).getV() : v, removalCause);
            }
        };
    }
}
