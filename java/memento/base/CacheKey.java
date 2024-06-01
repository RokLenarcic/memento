package memento.base;

import clojure.lang.Util;

import java.util.Objects;

public class CacheKey {
    private final Object id;
    private final Object args;

    private final int _hq;

    public CacheKey(Object id, Object args) {
        this.id = id;
        this.args = args;
        this._hq = 31 * id.hashCode() + Util.hasheq(args);
    }

    public Object getId() {
        return id;
    }

    public Object getArgs() {
        return args;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return _hq == cacheKey._hq && Objects.equals(id, cacheKey.id) && Util.equiv(args, cacheKey.args);
    }

    @Override
    public int hashCode() {
        return _hq;
    }

    @Override
    public String toString() {
        return "CacheKey{" +
                "id=" + id +
                ", args=" + args +
                '}';
    }
}
