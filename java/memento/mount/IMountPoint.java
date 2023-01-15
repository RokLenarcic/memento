package memento.mount;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import memento.base.ICache;
import memento.base.Segment;

/**
 * Interface for cache mount
 */
public interface IMountPoint {
    /**
     * Returns the cache as a map. This does not imply a snapshot,
     * as implementation might provide a weakly consistent view of the cache.
     * @return
     */
    IPersistentMap asMap();

    /**
     * Return cached value, possibly invoking the function with the args to
     *         obtain the value. This should be a thread-safe atomic operation.
     * @param args
     * @return
     */
    Object cached(ISeq args);

    /**
     * Return cached value if present in cache or memento.base/absent otherwise.
     * @param args
     * @return
     */
    Object ifCached(ISeq args);

    /**
     * Coll of tags for this mount point
     * @return
     */
    Object getTags();

    /**
     * Handles event using internal event handling mechanism, usually a function
     * @param event
     * @return
     */
    Object handleEvent(Object event);

    /**
     * Invalidate entry for args, returns Cache
     * @param args
     * @return
     */
    ICache invalidate(ISeq args);

    /**
     * Invalidate all entries, returns Cache
     * @return
     */
    ICache invalidateAll();

    /**
     * Returns currently mounted Cache.
     * @return
     */
    ICache mountedCache();

    /**
     * Return segment
     * @return
     */
    Segment segment();

    /**
     * Add entries to cache, returns Cache.
     * @param argsToVals
     * @return
     */
    ICache addEntries(IPersistentMap argsToVals);
}
