package memento.caffeine;

import clojure.lang.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import memento.base.CacheKey;
import memento.base.EntryMeta;
import memento.base.LockoutMap;
import memento.base.Segment;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

public class CaffeineCache_ {

    private final BiFunction<Segment, ISeq, CacheKey> keyFn;

    private final ConcurrentHashMap<Object, Set<CacheKey>> secIndex;
    private final IFn retFn;

    private final Cache<Object, Joinable> delegate;

    public CaffeineCache_(Caffeine<Object, Object> builder, final IFn keyFn, final IFn retFn, ConcurrentHashMap<Object, Set<CacheKey>> secIndex) {
        this.keyFn = keyFn == null ?
                (segment, args) -> new CacheKey(segment.getId(), segment.getKeyFn().invoke(args)) :
                (segment, args) -> new CacheKey(segment.getId(), keyFn.invoke(segment.getKeyFn().invoke(args)));
        this.retFn = retFn;
        this.delegate = builder.build();
        this.secIndex = secIndex;
    }

    public Object cached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        SpecialPromise p;
        do {
            p = new SpecialPromise();
            // check for ongoing load
            Joinable joinable = delegate.asMap().putIfAbsent(key, p);
            if (joinable == null) {
                Object result;
                try {
                    // mark load start on promise to detect circular loads
                    p.markLoadStart(key);
                    // calculate value
                    result = AFn.applyToHelper(segment.getF(), args);
                    if (retFn != null) {
                        result = retFn.invoke(args, result);
                    }
                    if (LockoutMap.INSTANCE.awaitLockout(segment, result) || p.isInvalid()) {
                        // value was invalidated during load
                        delegate.asMap().remove(key, p);
                        continue;
                    }
                    // if valid add to secondary index
                    SecondaryIndexOps.secIndexConj(secIndex, key, result);
                    if (result instanceof EntryMeta && ((EntryMeta) result).isNoCache()) {
                        delegate.asMap().remove(key, p);
                    }
                    p.deliver(result);
                    return EntryMeta.unwrap(result);
                } catch (Throwable t) {
                    delegate.invalidate(key);
                    p.deliverException(t);
                    throw t;
                }
            } else {
                // join into ongoing load
                Object ret = joinable.join();
                if (!LockoutMap.INSTANCE.awaitLockout(segment, ret) && !joinable.isInvalid()) {
                    // if not invalidated, return the value
                    return EntryMeta.unwrap(ret);
                }
                // else try to initiate load again
            }
        } while (true);
    }

    public Object ifCached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        Joinable v = delegate.getIfPresent(key);
        if (v == null) {
            return EntryMeta.absent;
        } else {
            Object ret = v.getNow(EntryMeta.absent);
            return ret == EntryMeta.absent || LockoutMap.INSTANCE.awaitLockout(segment, ret) || v.isInvalid() ?
                    EntryMeta.absent : EntryMeta.unwrap(ret);
        }
    }

    public void invalidate(Segment segment) {
        final Iterator<Object> iter = delegate.asMap().keySet().iterator();
        while (iter.hasNext()) {
            CacheKey it = (CacheKey) iter.next();
            if (it.getId().equals(segment.getId())) {
                iter.remove();
            }
        }
    }

    public void invalidate(Segment segment, ISeq args) {
        delegate.invalidate(keyFn.apply(segment, args));
    }

    public void invalidateAll() {
        delegate.invalidateAll();
    }

    public void invalidateIds(Iterable<Object> ids) {
        for(Object id : ids) {
            Set<CacheKey> cacheKeys = secIndex.remove(id);
            if (cacheKeys != null) {
                delegate.invalidateAll(cacheKeys);
            }
        }
    }

    public void addEntries(Segment segment, IPersistentMap argsToVals) {
        for(Object o : argsToVals) {
            MapEntry entry = (MapEntry)o;
            CacheKey key = keyFn.apply(segment, RT.seq(entry.getKey()));
            SecondaryIndexOps.secIndexConj(secIndex, key, entry.getValue());
            delegate.put(key, SpecialPromise.completed(entry.getValue()));
        }
    }

    public ConcurrentMap<Object, Joinable> asMap() {
        return delegate.asMap();
    }

    public CacheStats stats() {
        return delegate.stats();
    }
    public void loadData(APersistentMap map) {
        map.forEach((Object k, Object v) -> {
            List<Object> list = (List<Object>) k;
            CacheKey key = new CacheKey(list.get(0), list.get(1));
            SecondaryIndexOps.secIndexConj(secIndex, key, v);
            delegate.put(key, SpecialPromise.completed(v));
        });
    }
}
