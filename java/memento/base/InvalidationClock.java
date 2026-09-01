package memento.base;

import java.util.concurrent.atomic.AtomicLong;

public final class InvalidationClock {
    public static final long NO_INVALIDATION_EPOCH = Long.MIN_VALUE;
    public static final long FIRST_EPOCH = Long.MIN_VALUE + 1;

    private static final AtomicLong clock = new AtomicLong(FIRST_EPOCH);

    public static long current() {
        return clock.get();
    }

    // Returns the current epoch for an invalidation, then advances the clock for subsequent writes.
    public static long claimInvalidationEpoch() {
        return clock.getAndIncrement();
    }

    // Returns a unique epoch for a cache write/load, advancing the clock for subsequent invalidations and writes.
    public static long claimWriteEpoch() {
        return clock.getAndIncrement();
    }

    // Reserves count unique write epochs and returns the first one.
    public static long reserveWriteEpochs(int count) {
        return clock.getAndAdd(count);
    }

    public static void foreignUpdate(long foreignEpoch) {
        clock.getAndUpdate(v -> Long.max(foreignEpoch, v));
    }

}
