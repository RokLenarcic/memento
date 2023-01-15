package memento.caffeine;

import java.util.Objects;

public class CacheKey {
    private final Object id;
    private final Object args;

    public CacheKey(Object id, Object args) {
        this.id = id;
        this.args = args;
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
        return Objects.equals(id, cacheKey.id) && Objects.equals(args, cacheKey.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, args);
    }
}
