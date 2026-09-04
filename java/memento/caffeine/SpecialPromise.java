package memento.caffeine;

import memento.base.EntryMeta;
import memento.base.InvalidationTimeline;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Special promise for use in indirection in Caffeine cache. Do not use otherwise.
 * <p>
 * This class is intended to be created and delivered by a single loader thread, while other
 * threads await the result via {@link #await(Object)}. The loader is detected so that recursive
 * loads on the same key throw {@link StackOverflowError} instead of deadlocking.
 * <p>
 * The loader thread calls {@link #deliver(Object, long)} at most once. Concurrent invalidation
 * from other threads (via {@link #invalidate()}) is supported
 * and serialized through CAS on {@code result}: only one of {@code deliver}, {@code reject},
 * {@code deliverException}, or {@code invalidate} can publish a terminal value. Once
 * {@code result} has been CAS'd from {@code null} to a terminal value, all subsequent attempts
 * become no-ops. {@code deliver} returns {@code true} only if it actually published the value;
 * if a concurrent {@code invalidate} won the CAS first, {@code deliver} returns {@code false}
 * and the loader is expected to discard the entry.
 */
public class SpecialPromise {

    private static final int AVAILABLE = 0;
    private static final int INTERRUPTING = 1;
    private static final int COMPLETE = 2;
    private static final AltResult NIL = new AltResult(null);
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<SpecialPromise, Object> RESULT = AtomicReferenceFieldUpdater.newUpdater(SpecialPromise.class, Object.class, "result");
    private static final AtomicIntegerFieldUpdater<SpecialPromise> STATE = AtomicIntegerFieldUpdater.newUpdater(SpecialPromise.class, "state");
    private final CountDownLatch d = new CountDownLatch(1);
    private volatile Thread thread;
    private final long epoch;
    private volatile InvalidationTimeline.Operation timelineStart;
    private volatile Object result;
    private volatile int state = AVAILABLE;

    SpecialPromise(long epoch) {
        this.epoch = epoch;
    }

    /**
     * Mark the current thread as the loader and retain its timeline start. Publishing the
     * owner first lets a concurrent invalidation interrupt it while the pin is installed.
     */
    void startLoad(InvalidationTimeline.Operation start) {
        thread = Thread.currentThread();
        timelineStart = start;
    }

    Object await(Object stackOverflowContext) throws Throwable {
        if (thread != null && thread == Thread.currentThread()) {
            throw new StackOverflowError("Recursive load on key: " + stackOverflowContext);
        }
        Object r;
        if ((r = result) == null) {
            d.await();
            r = result;
        }
        if (r instanceof AltResult) {
            Throwable x = ((AltResult) r).value;
            if (x == null) {
                return null;
            } else {
                throw x;
            }
        } else {
            return r;
        }
    }

    // Returns true if no segment, cache, or result-derived secondary-ID invalidation
    // predates the load and this call won the result CAS.
    boolean deliver(Object r, long latestInvalidation) {
        InvalidationTimeline.Operation start = timelineStart;
        if (epoch <= latestInvalidation
                || (start != null && r instanceof EntryMeta
                && SecondaryIndex.INSTANCE.isInvalid(start, (EntryMeta) r))) {
            RESULT.compareAndSet(this, null, EntryMeta.absent);
            return false;
        }
        Object published = r == null ? NIL : r;
        // A concurrent invalidate() won the race and set result=absent.
        return RESULT.compareAndSet(this, null, published);
    }


    void reject() {
        // Force absent regardless of any previously-published value when publication
        // validation fails, so joiners cannot observe a discarded result.
        result = EntryMeta.absent;
    }

    boolean deliverException(Throwable t) {
        return RESULT.compareAndSet(this, null, new AltResult(t));
    }

    /**
     * Non-blocking availability probe used by if-cached/as-map style callers.
     * <p>
     * Returns {@link EntryMeta#absent} when the promise is still pending, was
     * invalidated, has not published a result, or completed with an exception.
     * Unlike {@link #await(Object)}, this method never propagates loader
     * exceptions: callers are asking whether a usable cached value is available
     * right now, not joining the load.
     */
    public Object getNow() {
        Object r;
        if (d.getCount() != 0) {
            return EntryMeta.absent;
        }
        if ((r = result) instanceof AltResult) {
            Throwable x = ((AltResult) r).value;
            if (x == null) {
                return null;
            } else {
                return EntryMeta.absent;
            }
        } else {
            return r == null ? EntryMeta.absent : r;
        }
    }

    void invalidate() {
        // Invalidate always wins: clobber any prior value with absent so joiners blocked
        // in await() observe a miss and re-loop. The loader, if still running, will see
        // isInvalid()==true after its own deliver() and discard its entry. The caller of
        // invalidate is responsible for removing the entry from the delegate map.
        result = EntryMeta.absent;
        releaseTimeline();
        Thread t = thread;
        if (t != null) {
            if (STATE.compareAndSet(this, AVAILABLE, INTERRUPTING)) {
                t.interrupt();
                state = COMPLETE;
            }
        }
    }

    boolean isInvalid() {
        return result == EntryMeta.absent;
    }

    void releaseResult() {
        if (!STATE.compareAndSet(this, AVAILABLE, COMPLETE)) {
            while (state == INTERRUPTING) {
                Thread.onSpinWait();
            }
            Thread.interrupted();
        }
        releaseTimeline();
        d.countDown();
    }

    void releaseTimeline() {
        timelineStart = null;
    }

    private static class AltResult {
        Throwable value;

        public AltResult(Throwable value) {
            this.value = value;
        }
    }
}
