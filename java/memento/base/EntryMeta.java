package memento.base;

import clojure.lang.APersistentSet;
import clojure.lang.IPersistentSet;
import clojure.lang.PersistentHashSet;

import java.util.Objects;

public class EntryMeta {

    public static final Object absent = new Object();

    public static Object unwrap(Object o) {
        return o instanceof EntryMeta ? ((EntryMeta)o).getV() : o;
    }
    private Object v;
    private boolean noCache;
    private IPersistentSet tagIdents;

    public EntryMeta(Object v, boolean noCache, IPersistentSet tagIdents) {
        this.v = v;
        this.noCache = noCache;
        this.tagIdents = tagIdents == null ? PersistentHashSet.EMPTY : tagIdents;
    }

    public Object getV() {
        return v;
    }

    public void setV(Object v) {
        this.v = v;
    }

    public boolean isNoCache() {
        return noCache;
    }

    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public IPersistentSet getTagIdents() {
        return tagIdents;
    }

    public void setTagIdents(IPersistentSet tagIdents) {
        this.tagIdents = tagIdents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntryMeta entryMeta = (EntryMeta) o;
        return noCache == entryMeta.noCache && Objects.equals(v, entryMeta.v) && tagIdents.equals(entryMeta.tagIdents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(v, noCache, tagIdents);
    }

    @Override
    public String toString() {
        return "EntryMeta{" +
                "v=" + v +
                ", noCache=" + noCache +
                ", tagIdents=" + tagIdents +
                '}';
    }
}
