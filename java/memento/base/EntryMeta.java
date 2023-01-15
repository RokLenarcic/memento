package memento.base;

import clojure.lang.IPersistentSet;

import java.util.Objects;

public class EntryMeta {
    private Object v;
    private boolean noCache;
    private IPersistentSet tagIdents;

    public EntryMeta(Object v, boolean noCache, IPersistentSet tagIdents) {
        this.v = v;
        this.noCache = noCache;
        this.tagIdents = tagIdents;
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
}
