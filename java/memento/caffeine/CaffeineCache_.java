package memento.caffeine;

import clojure.lang.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import memento.base.*;

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

    private volatile long cacheEpoch = InvalidationClock.NO_INVALIDATION_EPOCH;

    private long lastInvalidation(Segment segment) {
        return Long.max(segment.getInvalidationEpoch(), cacheEpoch);
    }

    private long lastInvalidation(Segment segment, IPersistentSet tagsAndIds) {
        long epoch = Long.max(segment.getInvalidationEpoch(), cacheEpoch);
        return Long.max(epoch, TagInvalidation.INSTANCE.lastInvalidatedEpoch(tagsAndIds));
    }

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
        loads.add(promise);
    }

    public Object cached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        do {
            SpecialPromise p = new SpecialPromise(InvalidationClock.current());
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
                    if (p.deliver(result, lastInvalidation(segment))) {
                        if (result instanceof EntryMeta && ((EntryMeta) result).isNoCache()) {
                            delegate.asMap().remove(key, p);
                        } else {
                            CacheEntry entry = CacheEntry.fromResult(result, InvalidationClock.claimWriteEpoch());
                            // if valid add to secondary index
                            secIndex.add(key, entry);
                            if (p.isInvalid() || p.hasInvalidatedTagId(entry.getTagIdents()) || entry.getWriteEpoch() <= lastInvalidation(segment)) {
                                secIndex.removeKeys(key, entry);
                                delegate.asMap().remove(key, p);
                                continue;
                            }
                            if (!delegate.asMap().replace(key, p, entry)) {
                                secIndex.removeKeys(key, entry);
                                p.reject();
                                continue;
                            }
                            // Publication of the CacheEntry to the delegate map is the
                            // sole source of truth for the value. Reject the promise so
                            // joiners blocked in await() re-loop and revalidate against
                            // the map (or its successor) instead of taking our value via
                            // the promise channel.
                            p.reject();
                        }
                    } else {
                        delegate.asMap().remove(key, p);
                        continue;
                    }
                    return EntryMeta.unwrap(result);
                } catch (Throwable t) {
                    delegate.asMap().remove(key, p);
                    if (p.isInvalid()) {
                        Thread.interrupted();
                    } else {
                        p.deliverException(retExFn == null ? t : (Throwable) retExFn.invoke(args, t));
                        throw t;
                    }
                } finally {
                    loads.remove(p);
                    p.releaseResult();
                }
            } else {
                // join into ongoing load
                if (cached instanceof SpecialPromise) {
                    SpecialPromise sp = (SpecialPromise) cached;
                    Object ret = sp.await(key);
                    if (ret != EntryMeta.absent) {
                        // if not invalidated, return the value
                        return EntryMeta.unwrap(ret);
                    }
                } else {
                    CacheEntry entry = (CacheEntry) cached;
                    if (entry.getWriteEpoch() <= lastInvalidation(segment, entry.getTagIdents())) {
                        delegate.asMap().remove(key, entry);
                        continue;
                    }
                    return CacheEntry.unwrap(entry);
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
            Object ret = p.getNow();
            return ret == absent ? absent : EntryMeta.unwrap(ret);
        } else {
            CacheEntry entry = (CacheEntry) v;
            if (entry.getWriteEpoch() <= lastInvalidation(segment, entry.getTagIdents())) {
                delegate.asMap().remove(key, entry);
                return absent;
            }
            return CacheEntry.unwrap(entry);
        }
    }

    public void invalidate(Segment segment) {
        segment.invalidateAt(InvalidationClock.claimInvalidationEpoch());
        final Iterator<Map.Entry<CacheKey, Object>> iter = delegate.asMap().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<CacheKey, Object> it = iter.next();
            if (it.getKey().getId().equals(segment.getId())) {
                Object v = it.getValue();
                if (v instanceof SpecialPromise) {
                    ((SpecialPromise) v).invalidate();
                }
                iter.remove();
            }
        }
    }

    public void invalidate(Segment segment, ISeq args) {
        Object v = delegate.asMap().remove(keyFn.apply(segment, args));
        if (v instanceof SpecialPromise) {
            ((SpecialPromise) v).invalidate();
        }
    }

    public void invalidateAll() {
        cacheEpoch = InvalidationClock.claimInvalidationEpoch();
        delegate.invalidateAll();
    }

    public void invalidateIds(Iterable<Object> ids) {
        ArrayList<Object> idList = new ArrayList<>();
        for (Object id : ids) {
            idList.add(id);
        }
        final long invalidationEpoch = InvalidationClock.claimInvalidationEpoch();
        loads.forEach(load -> load.addInvalidIds(idList));
        ConcurrentMap<CacheKey, Object> map = delegate.asMap();
        for (Object id : idList) {
            secIndex.removeIf(id, indexEntry -> {
                CacheKey k = indexEntry.getKey();
                Object current = map.get(k);
                if (current instanceof SpecialPromise) {
                    if (map.remove(k, current)) {
                        ((SpecialPromise) current).invalidate();
                        return true;
                    }
                    return false;
                } else if (current != null) {
                    CacheEntry entry = (CacheEntry) current;
                    if (entry.getWriteEpoch() != indexEntry.getWriteEpoch()) {
                        return true;
                    } else if (entry.getWriteEpoch() < invalidationEpoch && entry.hasTagIdent(id)) {
                        map.remove(k, current);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            });
        }
    }

    public void addEntries(Segment segment, IPersistentMap argsToVals) {
        for (Object o : argsToVals) {
            MapEntry entry = (MapEntry) o;
            CacheKey key = keyFn.apply(segment, RT.seq(entry.getKey()));
            Object val = entry.getValue();
            if (val instanceof EntryMeta && ((EntryMeta) val).isNoCache()) {
                delegate.invalidate(key);
            } else {
                CacheEntry cacheEntry = CacheEntry.fromResult(val, InvalidationClock.claimWriteEpoch());
                delegate.put(key, cacheEntry);
                secIndex.add(key, cacheEntry);
            }
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
            if (v instanceof EntryMeta && ((EntryMeta) v).isNoCache()) {
                delegate.invalidate(key);
            } else {
                CacheEntry entry = CacheEntry.fromResult(v, InvalidationClock.claimWriteEpoch());
                delegate.put(key, entry);
                secIndex.add(key, entry);
            }
        });
    }

    public static RemovalListener<CacheKey, Object> listener(IFn removalListener) {
        return (k, v, removalCause) -> {
            if (!(v instanceof SpecialPromise)) {
                removalListener.invoke(k.getId(), k.getArgs(), CacheEntry.unwrap(v), removalCause);
            }
        };
    }
}
