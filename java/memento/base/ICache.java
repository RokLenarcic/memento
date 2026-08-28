package memento.base;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;

/**
 * Protocol for Cache. It houses entries for multiple functions.
 * <p>
 * Most functions receive a Segment object that should be used to partition for different functions
 * and using other :
 * - id: use for separating caches, it is either name specified by user's config, or var name or function object
 * - key-fn: key-fn from mount point, use this to generate cache key
 * - f: use this function to load values
 */
public interface ICache {
    /**
     * Return the conf for this cache.
     *
     * @return
     */
    IPersistentMap conf();

    /**
     * Return the cache value.
     * <p>
     * - segment is Segment record provided by the mount point, it contains information that allows Cache
     * to separate caches for different functions
     *
     * @param segment
     * @param args
     * @return
     */
    Object cached(Segment segment, ISeq args);

    /**
     * Return cached value if present (and available immediately) in cache or memento.base/absent otherwise.
     *
     * @param segment
     * @param args
     * @return
     */
    Object ifCached(Segment segment, ISeq args);

    /**
     * Invalidate all the entries linked a mount's single arg list, return Cache
     *
     * @param segment
     * @return
     */
    ICache invalidate(Segment segment);

    /**
     * Invalidate all the entries linked to a mount, return Cache.
     * <p>
     * <b>Concurrency contract:</b> implementations must guarantee that joiners awoken by
     * any signal sent to an in-flight load for this key either (a) observe the entry as
     * absent from the cache's primary store and re-load, or (b) observe a freshly-published
     * {@link CacheEntry} from a load that started <i>after</i> this invalidation. They must
     * never observe the value of the just-invalidated promise.
     * <p>
     * Caffeine-backed implementations achieve this by removing the entry from the delegate
     * map <i>before</i> signalling {@code SpecialPromise.invalidate()} — the map-level CAS
     * interlocks with the loader's subsequent {@code deliver}/publish step. Backends without
     * such atomic interlock (e.g. Redis) achieve it by signalling the promise first: the
     * loader's {@code deliver} then returns false and the loader abandons its write, after
     * which the primary store is cleared. Either ordering is acceptable provided the above
     * guarantee holds.
     * <p>
     * Note: in-flight loaders may have their thread interrupt flag set as a side
     * effect of this call. Loader paths that catch {@link Throwable} clear the
     * interrupt flag with {@link Thread#interrupted()} when discarding an invalidated
     * load; this can swallow an unrelated interrupt that arrives in the same window.
     *
     * @param segment
     * @param args
     * @return
     */
    ICache invalidate(Segment segment, ISeq args);

    /**
     * Invalidate all entries, returns Cache
     *
     * @return
     */
    ICache invalidateAll();

    /**
     * Add entries as for a function
     *
     * @param segment
     * @param argsToVals
     * @return
     */
    ICache addEntries(Segment segment, IPersistentMap argsToVals);

    /**
     * Return all entries in the cache with keys shaped like as per cache implementation.
     *
     * @return
     */
    IPersistentMap asMap();

    /**
     * Return all entries in the cache for a mount with keys shaped like as per cache implementation.
     *
     * @param segment
     * @return
     */
    IPersistentMap asMap(Segment segment);
}
