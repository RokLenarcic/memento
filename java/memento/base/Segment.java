package memento.base;

import clojure.lang.IFn;

import java.util.Objects;

// Segment has 3 properties:
// - fn to run
// - key-fn to apply for keys from this segment
// - segment ID, use this rather than f to separate segments in cache
public class Segment {
    private IFn f;
    private IFn keyFn;
    private Object id;

    private volatile boolean lockout = false;

    public Segment(IFn f, IFn keyFn, Object id) {
        this.f = f;
        this.keyFn = keyFn;
        this.id = id;
    }

    public IFn getF() {
        return f;
    }

    public void setF(IFn f) {
        this.f = f;
    }

    public IFn getKeyFn() {
        return keyFn;
    }

    public void setKeyFn(IFn keyFn) {
        this.keyFn = keyFn;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
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

    @Override
    public String toString() {
        return "Segment{" +
                "f=" + f +
                ", keyFn=" + keyFn +
                ", id=" + id +
                '}';
    }

    public boolean isLockout() {
        return lockout;
    }

    public void setLockout(boolean lockout) {
        this.lockout = lockout;
    }
}
