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
     * <b>Concurrency contract:</b> implementations must remove the entry from their
     * primary key&rarr;value map <i>before</i> signalling any in-flight load promise
     * for that key (e.g. via {@code SpecialPromise.invalidate()}). This ordering
     * guarantees that awaking joiners which re-loop through the map either find the
     * entry absent or find a freshly-published {@link CacheEntry} from a subsequent
     * load, but never observe the just-invalidated promise's value.
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
     * Invalidate entries with these secondary IDs, returns Cache. Each ID is a pair of tag and object.
     * <p>
     * <b>Concurrency contract:</b> this method coordinates only with loads that are already
     * registered within this cache (e.g. the local Caffeine {@code loads} set). It does
     * <i>not</i> by itself update {@link TagInvalidation}, so loads that are in flight in
     * <i>other</i> caches, or loads that have not yet been registered locally, may still
     * publish stale results.
     * <p>
     * Callers that need cross-cache or globally-visible tag invalidation must wrap calls
     * to {@code invalidateIds} in a {@link TagInvalidation#startInvalidation} /
     * {@link TagInvalidation#endInvalidation} window using an epoch obtained from
     * {@link InvalidationClock#claimInvalidationEpoch()}. The public
     * {@code memento.core/memo-clear-tags!} entry point already does this; direct callers
     * (e.g. cross-process invalidation listeners) must do the equivalent themselves.
     *
     * @param id
     * @return
     */
    ICache invalidateIds(Iterable<Object> id);

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
