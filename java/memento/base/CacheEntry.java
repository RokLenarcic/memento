package memento.base;

import clojure.lang.IPersistentSet;
import clojure.lang.PersistentHashSet;

/**
 * Internal cache storage entry. User code may return EntryMeta to annotate a
 * result, but completed cache values are stored as CacheEntry.
 */
public final class CacheEntry {
    private final Object value;
    private final IPersistentSet secIds;
    private final long writeEpoch;

    public CacheEntry(Object value, IPersistentSet secIds, long writeEpoch) {
        this.value = value;
        this.secIds = secIds == null ? PersistentHashSet.EMPTY : secIds;
        this.writeEpoch = writeEpoch;
    }

    public static CacheEntry fromResult(Object result, long writeEpoch) {
        if (result instanceof EntryMeta) {
            EntryMeta entryMeta = (EntryMeta) result;
            if (entryMeta.isNoCache()) {
                throw new IllegalArgumentException("No-cache results are not stored cache entries");
            }
            return new CacheEntry(entryMeta.getV(), entryMeta.getSecIds(), writeEpoch);
        }
        return new CacheEntry(result, PersistentHashSet.EMPTY, writeEpoch);
    }

    public static Object unwrap(Object o) {
        return o instanceof CacheEntry ? ((CacheEntry) o).getValue() : EntryMeta.unwrap(o);
    }

    public Object getValue() {
        return value;
    }

    public IPersistentSet getSecIds() {
        return secIds;
    }

    public long getWriteEpoch() {
        return writeEpoch;
    }

    public boolean hasSecId(Object secId) {
        return secIds.contains(secId);
    }
}
