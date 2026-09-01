package memento.base;

import clojure.lang.IPersistentSet;
import clojure.lang.PersistentHashSet;

import java.util.Objects;

public class EntryMeta {

    public static final Object absent = new Object();

    public static Object unwrap(Object o) {
        return o instanceof EntryMeta ? ((EntryMeta) o).getV() : o;
    }

    private Object v;
    private boolean noCache;
    private IPersistentSet secIds;

    public EntryMeta(Object v, boolean noCache, IPersistentSet secIds) {
        this.v = v;
        this.noCache = noCache;
        this.secIds = secIds == null ? PersistentHashSet.EMPTY : secIds;
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

    public IPersistentSet getSecIds() {
        return secIds;
    }

    public void setSecIds(IPersistentSet secIds) {
        this.secIds = secIds == null ? PersistentHashSet.EMPTY : secIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntryMeta entryMeta = (EntryMeta) o;
        return noCache == entryMeta.noCache && Objects.equals(v, entryMeta.v) && secIds.equals(entryMeta.secIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(v, noCache, secIds);
    }

    @Override
    public String toString() {
        return "EntryMeta{" +
                "v=" + v +
                ", noCache=" + noCache +
                ", secIds=" + secIds +
                '}';
    }
}
