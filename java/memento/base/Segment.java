package memento.base;

import clojure.lang.IFn;
import clojure.lang.IPersistentMap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

// Segment has properties:
// - fn to run
// - key-fn to apply for keys from this segment
// - segment ID, use this rather than f to separate segments in cache
// - conf is mount point (or segment) conf
public class Segment {
    private static final class InvalidationState {
        // Monotonic non-decreasing epoch; updated only via Segment.invalidateAt which
        // takes the max of the current value and the provided epoch.
        volatile long segmentEpoch = InvalidationClock.NO_INVALIDATION_EPOCH;
    }

    private static final AtomicLongFieldUpdater<InvalidationState> EPOCH_UPDATER =
            AtomicLongFieldUpdater.newUpdater(InvalidationState.class, "segmentEpoch");

    private final IFn f;
    private final IFn keyFn;
    private final Object id;

    private final IPersistentMap conf;
    private final InvalidationState invalidationState;

    public Segment(IFn f, IFn keyFn, Object id, IPersistentMap conf) {
        this(f, keyFn, id, conf, new InvalidationState());
    }

    private Segment(IFn f, IFn keyFn, Object id, IPersistentMap conf, InvalidationState invalidationState) {
        this.f = f;
        this.keyFn = keyFn;
        this.id = id;
        this.conf = conf;
        this.invalidationState = invalidationState;
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

    public long getInvalidationEpoch() {
        return invalidationState.segmentEpoch;
    }

    public void invalidateAt(long epoch) {
        // Monotonic: never move the epoch backwards. Concurrent invalidations only widen
        // the window; readers always observe a value >= any previously observed value.
        long current;
        do {
            current = invalidationState.segmentEpoch;
            if (epoch <= current) {
                return;
            }
        } while (!EPOCH_UPDATER.compareAndSet(invalidationState, current, epoch));
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
        return new Segment(newF, keyFn, id, conf, invalidationState);
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
