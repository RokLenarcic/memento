package memento.base;

import clojure.lang.IFn;
import clojure.lang.IPersistentMap;

import java.util.Objects;

// Segment has properties:
// - fn to run
// - key-fn to apply for keys from this segment
// - segment ID, use this rather than f to separate segments in cache
// - conf is mount point (or segment) conf
// - expiry is Segment specific expiry
public class Segment {
    private final IFn f;
    private final IFn keyFn;
    private final Object id;

    private final IPersistentMap conf;

    public Segment(IFn f, IFn keyFn, Object id, IPersistentMap conf) {
        this.f = f;
        this.keyFn = keyFn;
        this.id = id;
        this.conf = conf;
    }

    public IFn getF() {
        return f;
    }

    public IFn getKeyFn() {
        return keyFn;
    }


    public Object getId() {
        return id;
    }

    public IPersistentMap getConf() {
        return conf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Segment segment = (Segment) o;
        return keyFn.equals(segment.keyFn) && id.equals(segment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(f, keyFn, id);
    }

    public Segment withFn(IFn newF) {
        return new Segment(newF, keyFn, id, conf);
    }

    @Override
    public String toString() {
        return "Segment{" +
                "f=" + f +
                ", keyFn=" + keyFn +
                ", id=" + id +
                ", conf=" + conf +
                '}';
    }
}
