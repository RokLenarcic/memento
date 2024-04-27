package memento.base;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;

/**
 * Protocol for Cache. It houses entries for multiple functions.
 *
 * Most functions receive a Segment object that should be used to partition for different functions
 * and using other :
 * - id: use for separating caches, it is either name specified by user's config, or var name or function object
 * - key-fn: key-fn from mount point, use this to generate cache key
 * - f: use this function to load values
 */
public interface ICache {
    /**
     * Return the conf for this cache.
     * @return
     */
    IPersistentMap conf();

    /**
     * Return the cache value.
     *
     * - segment is Segment record provided by the mount point, it contains information that allows Cache
     * to separate caches for different functions
     * @param segment
     * @param args
     * @return
     */
    Object cached(Segment segment, ISeq args);

    /**
     * Return cached value if present (and available immediately) in cache or memento.base/absent otherwise.
     * @param segment
     * @param args
     * @return
     */
    Object ifCached(Segment segment, ISeq args);

    /**
     * Invalidate all the entries linked a mount's single arg list, return Cache
     * @param segment
     * @return
     */
    ICache invalidate(Segment segment);

    /**
     * Invalidate all the entries linked to a mount, return Cache
     * @param segment
     * @param args
     * @return
     */
    ICache invalidate(Segment segment, ISeq args);

    /**
     * Invalidate all entries, returns Cache
     * @return
     */
    ICache invalidateAll();

    /**
     * Invalidate entries with these secondary IDs, returns Cache. Each ID is a pair of tag and object
     * @param id
     * @return
     */
    ICache invalidateIds(Iterable<Object> id);

    /**
     * Add entries as for a function
     * @param segment
     * @param argsToVals
     * @return
     */
    ICache addEntries(Segment segment, IPersistentMap argsToVals);

    /**
     * Return all entries in the cache with keys shaped like as per cache implementation.
     * @return
     */
    IPersistentMap asMap();

    /**
     * Return all entries in the cache for a mount with keys shaped like as per cache implementation.
     * @param segment
     * @return
     */
    IPersistentMap asMap(Segment segment);
}
