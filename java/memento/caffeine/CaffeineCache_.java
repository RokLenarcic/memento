package memento.caffeine;

import clojure.lang.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import memento.base.*;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

public class CaffeineCache_ {

    private final BiFunction<Segment, ISeq, CacheKey> keyFn;

    private final IFn retFn;

    private final IFn retExFn;

    private final Cache<CacheKey, Object> delegate;

    private volatile long cacheEpoch = InvalidationClock.NO_INVALIDATION_EPOCH;

    private long lastInvalidation(Segment segment) {
        return Long.max(segment.getInvalidationEpoch(), cacheEpoch);
    }

    private long lastInvalidation(Segment segment, IPersistentSet ids) {
        return Long.max(lastInvalidation(segment), SecondaryIndex.INSTANCE.lastInvalidatedEpoch(ids));
    }

    public CaffeineCache_(Caffeine<Object, Object> builder, final IFn keyFn, final IFn retFn, final IFn retExFn) {
        this.keyFn = keyFn == null ?
                (segment, args) -> new CacheKey(segment.getId(), segment.getKeyFn().invoke(args)) :
                (segment, args) -> new CacheKey(segment.getId(), keyFn.invoke(segment.getKeyFn().invoke(args)));
        this.retFn = retFn;
        this.delegate = builder.build();
        this.retExFn = retExFn;
    }

    public Object cached(Segment segment, ISeq args) throws Throwable {
        CacheKey key = keyFn.apply(segment, args);
        do {
            SpecialPromise promise = new SpecialPromise(InvalidationClock.current());
            Object cached = delegate.asMap().putIfAbsent(key, promise);
            if (cached == null) {
                SecondaryIndex.LoadEntry load = SecondaryIndex.INSTANCE.addLoad(this, promise);
                try {
                    promise.ownerThread();
                    // calculate value
                    Object result = AFn.applyToHelper(segment.getF(), args);
                    if (retFn != null) {
                        result = retFn.invoke(args, result);
                    }
                    if (promise.deliver(result, lastInvalidation(segment))) {
                        if (result instanceof EntryMeta && ((EntryMeta) result).isNoCache()) {
                            IPersistentSet ids = ((EntryMeta) result).getTagIdents();
                            if (!SecondaryIndex.INSTANCE.finishLoad(load, ids, () -> delegate.asMap().remove(key, promise))) {
                                delegate.asMap().remove(key, promise);
                                promise.reject();
                                continue;
                            }
                        } else {
                            CacheEntry entry = CacheEntry.fromResult(result, InvalidationClock.claimWriteEpoch());
                            // if valid add to secondary index
                            SecondaryIndex.INSTANCE.add(this, key, entry);
                            if (!SecondaryIndex.INSTANCE.finishLoad(load, entry.getTagIdents(),
                                                                   () -> delegate.asMap().replace(key, promise, entry))) {
                                SecondaryIndex.INSTANCE.removeKeys(this, key, entry);
                                delegate.asMap().remove(key, promise);
                                promise.reject();
                                continue;
                            }
                        }
                    } else {
                        delegate.asMap().remove(key, promise);
                        continue;
                    }
                    return EntryMeta.unwrap(result);
                } catch (Throwable t) {
                    delegate.asMap().remove(key, promise);
                    if (promise.isInvalid() || promise.hasInvalidatedIds()) {
                        Thread.interrupted();
                    } else {
                        promise.deliverException(retExFn == null ? t : (Throwable) retExFn.invoke(args, t));
                        throw t;
                    }
                } finally {
                    SecondaryIndex.INSTANCE.removeLoad(load);
                    promise.releaseResult();
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

    public boolean invalidateIndexed(CacheKey key, long indexedEpoch, long invalidationEpoch) {
        Object current = delegate.asMap().computeIfPresent(key, (ignored, value) -> {
            if (value instanceof SpecialPromise) {
                ((SpecialPromise) value).invalidate();
                return null;
            }
            CacheEntry entry = (CacheEntry) value;
            return entry.getWriteEpoch() == indexedEpoch && entry.getWriteEpoch() < invalidationEpoch
                   ? null
                   : entry;
        });
        return current == null || ((CacheEntry) current).getWriteEpoch() != indexedEpoch;
    }

    public void addEntries(Segment segment, IPersistentMap argsToVals) {
        for (Object o : argsToVals) {
            MapEntry entry = (MapEntry) o;
            CacheKey key = keyFn.apply(segment, RT.seq(entry.getKey()));
            Object val = entry.getValue();
            if (val instanceof EntryMeta && ((EntryMeta) val).isNoCache()) {
                delegate.invalidate(key);
            } else {
                putEntry(key, CacheEntry.fromResult(val, InvalidationClock.claimWriteEpoch()));
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
                putEntry(key, CacheEntry.fromResult(v, InvalidationClock.claimWriteEpoch()));
            }
        });
    }

    private void putEntry(CacheKey key, CacheEntry entry) {
        if (entry.getTagIdents().count() == 0) {
            delegate.put(key, entry);
            return;
        }
        SpecialPromise invalidationTarget = new SpecialPromise(InvalidationClock.current());
        SecondaryIndex.LoadEntry load = SecondaryIndex.INSTANCE.addLoad(this, invalidationTarget);
        try {
            delegate.put(key, entry);
            SecondaryIndex.INSTANCE.add(this, key, entry);
            if (!SecondaryIndex.INSTANCE.finishLoad(load, entry.getTagIdents(), () -> true)) {
                SecondaryIndex.INSTANCE.removeKeys(this, key, entry);
                delegate.asMap().remove(key, entry);
            }
        } finally {
            SecondaryIndex.INSTANCE.removeLoad(load);
            invalidationTarget.releaseResult();
        }
    }

    public static RemovalListener<CacheKey, Object> listener(IFn removalListener) {
        return (k, v, removalCause) -> {
            if (!(v instanceof SpecialPromise)) {
                removalListener.invoke(k.getId(), k.getArgs(), CacheEntry.unwrap(v), removalCause);
            }
        };
    }
}
