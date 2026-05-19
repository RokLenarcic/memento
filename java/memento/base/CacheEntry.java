package memento.base;

import clojure.lang.IPersistentSet;
import clojure.lang.PersistentHashSet;

/**
 * Internal cache storage entry. User code may return EntryMeta to annotate a
 * result, but completed cache values are stored as CacheEntry.
 */
public final class CacheEntry {
    private final Object value;
    private final IPersistentSet tagIdents;
    private final long writeEpoch;

    public CacheEntry(Object value, IPersistentSet tagIdents, long writeEpoch) {
        this.value = value;
        this.tagIdents = tagIdents == null ? PersistentHashSet.EMPTY : tagIdents;
        this.writeEpoch = writeEpoch;
    }

    public static CacheEntry fromResult(Object result, long writeEpoch) {
        if (result instanceof EntryMeta) {
            EntryMeta entryMeta = (EntryMeta) result;
            if (entryMeta.isNoCache()) {
                throw new IllegalArgumentException("No-cache results are not stored cache entries");
            }
            return new CacheEntry(entryMeta.getV(), entryMeta.getTagIdents(), writeEpoch);
        }
        return new CacheEntry(result, PersistentHashSet.EMPTY, writeEpoch);
    }

    public static Object unwrap(Object o) {
        return o instanceof CacheEntry ? ((CacheEntry) o).getValue() : EntryMeta.unwrap(o);
    }

    public Object getValue() {
        return value;
    }

    public IPersistentSet getTagIdents() {
        return tagIdents;
    }

    public long getWriteEpoch() {
        return writeEpoch;
    }

    public boolean hasTagIdent(Object tagIdent) {
        return tagIdents.contains(tagIdent);
    }
}
