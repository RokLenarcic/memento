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

    private boolean beingInvalidated(Segment segment, CacheEntry entry) {
        return entry.getWriteEpoch() <= lastInvalidation(segment)
               || SecondaryIndex.INSTANCE.hasActiveInvalidation(entry.getSecIds());
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
                try {
                    memento.base.InvalidationTimeline.Operation operation = SecondaryIndex.INSTANCE.startOperation();
                    promise.startLoad(operation);
                    // calculate value
                    Object result = AFn.applyToHelper(segment.getF(), args);
                    if (retFn != null) {
                        result = retFn.invoke(args, result);
                    }
                    if (result instanceof EntryMeta) {
                        EntryMeta metadata = (EntryMeta) result;
                        IPersistentSet ids = metadata.getSecIds();
                        if (metadata.isNoCache()) {
                            if (delegate.asMap().remove(key, promise)
                                    && promise.deliver(result, lastInvalidation(segment))) {
                                return EntryMeta.unwrap(result);
                            } else {
                                promise.reject();
                                continue;
                            }
                        }
                        if (ids.count() != 0) {
                            CacheEntry entry = CacheEntry.fromResult(result, InvalidationClock.claimWriteEpoch());
                            SecondaryIndex.INSTANCE.add(this, key, entry);
                            if (promise.deliver(result, lastInvalidation(segment))
                            && delegate.asMap().replace(key, promise, entry)) {
                                return EntryMeta.unwrap(result);
                            } else {
                                SecondaryIndex.INSTANCE.removeKeys(this, key, entry);
                                delegate.asMap().remove(key, promise);
                                promise.reject();
                                continue;
                            }
                        }
                    }
                    // fall through for non-EntryMeta and EntryMeta that is not isNoCache and no Sec IDs
                    CacheEntry entry = CacheEntry.fromResult(result, InvalidationClock.claimWriteEpoch());
                    if (!promise.deliver(result, lastInvalidation(segment))) {
                        delegate.asMap().remove(key, promise);
                        promise.reject();
                        continue;
                    }
                    if (!delegate.asMap().replace(key, promise, entry)) {
                        promise.reject();
                        continue;
                    }
                    return EntryMeta.unwrap(result);
                } catch (Throwable t) {
                    delegate.asMap().remove(key, promise);
                    if (!promise.isInvalid()) {
                        Throwable delivered = retExFn == null ? t : (Throwable) retExFn.invoke(args, t);
                        if (promise.deliverException(delivered)) {
                            throw t;
                        }
                    }
                } finally {
                    promise.releaseResult();
                }
            } else {
                promise.releaseTimeline();
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
                    if (beingInvalidated(segment, entry)) {
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
            if (beingInvalidated(segment, entry)) {
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

    public void invalidateIndexed(CacheKey key, long indexedEpoch) {
        delegate.asMap().computeIfPresent(key, (ignored, value) -> {
            if (value instanceof SpecialPromise) {
                ((SpecialPromise) value).invalidate();
                return null;
            }
            CacheEntry entry = (CacheEntry) value;
            return entry.getWriteEpoch() == indexedEpoch ? null : entry;
        });
    }

    public void addEntries(Segment segment, IPersistentMap argsToVals) {
        long writeEpoch = InvalidationClock.reserveWriteEpochs(argsToVals.count());
        for (Object o : argsToVals) {
            MapEntry entry = (MapEntry) o;
            CacheKey key = keyFn.apply(segment, RT.seq(entry.getKey()));
            Object val = entry.getValue();
            if (val instanceof EntryMeta && ((EntryMeta) val).isNoCache()) {
                delegate.invalidate(key);
            } else {
                putEntry(key, CacheEntry.fromResult(val, writeEpoch));
            }
            writeEpoch++;
        }
    }

    public ConcurrentMap<CacheKey, Object> asMap() {
        return delegate.asMap();
    }

    public CacheStats stats() {
        return delegate.stats();
    }

    public void loadData(Map map) {
        long writeEpoch = InvalidationClock.reserveWriteEpochs(map.size());
        for (Object o : map.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            List<Object> list = (List<Object>) entry.getKey();
            CacheKey key = new CacheKey(list.get(0), list.get(1));
            Object value = entry.getValue();
            if (value instanceof EntryMeta && ((EntryMeta) value).isNoCache()) {
                delegate.invalidate(key);
            } else {
                putEntry(key, CacheEntry.fromResult(value, writeEpoch));
            }
            writeEpoch++;
        }
    }

    private void putEntry(CacheKey key, CacheEntry entry) {
        if (entry.getSecIds().count() == 0) {
            delegate.put(key, entry);
        } else {
            memento.base.InvalidationTimeline.Operation operation = SecondaryIndex.INSTANCE.startOperation();
            delegate.put(key, entry);
            SecondaryIndex.INSTANCE.add(this, key, entry);
            if (SecondaryIndex.INSTANCE.isInvalid(operation, entry)) {
                SecondaryIndex.INSTANCE.removeKeys(this, key, entry);
                delegate.asMap().remove(key, entry);
            }
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
