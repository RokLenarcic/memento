package memento.caffeine;

import clojure.lang.IPersistentSet;
import memento.base.EntryMeta;
import memento.base.TagInvalidation;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Special promise for use in indirection in Caffeine cache. Do not use otherwise.
 * <p>
 * This class is intended to be created and delivered by a single loader thread, while other
 * threads await the result via {@link #await(Object)}. The loader is detected so that recursive
 * loads on the same key throw {@link StackOverflowError} instead of deadlocking.
 * <p>
 * The loader thread calls {@link #deliver(Object, long)} at most once. Concurrent invalidation
 * from other threads (via {@link #invalidate()} or {@link #addInvalidIds(Iterable)}) is supported
 * and serialized through CAS on {@code result}: only one of {@code deliver}, {@code reject},
 * {@code deliverException}, or {@code invalidate} can publish a terminal value. Once
 * {@code result} has been CAS'd from {@code null} to a terminal value, all subsequent attempts
 * become no-ops. {@code deliver} returns {@code true} only if it actually published the value;
 * if a concurrent {@code invalidate} won the CAS first, {@code deliver} returns {@code false}
 * and the loader is expected to discard the entry.
 */
public class SpecialPromise {

    private static final AltResult NIL = new AltResult(null);
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<SpecialPromise, Object> RESULT =
            AtomicReferenceFieldUpdater.newUpdater(SpecialPromise.class, Object.class, "result");
    private final CountDownLatch d = new CountDownLatch(1);
    private final ConcurrentLinkedQueue<Object> invalidatedIds = new ConcurrentLinkedQueue<>();
    private final Thread thread;
    private final long epoch;
    private volatile Object result;

    public SpecialPromise(long epoch) {
        this.epoch = epoch;
        this.thread = Thread.currentThread();
    }

    public Object await(Object stackOverflowContext) throws Throwable {
        if (thread == Thread.currentThread()) {
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

    // Returns true if delivered object is viable AND was published (i.e. CAS won).
    public boolean deliver(Object r, long latestInvalidation) {
        if (result == EntryMeta.absent) {
            Thread.interrupted();
            return false;
        }
        IPersistentSet tagIdents = null;
        if (r instanceof EntryMeta) {
            tagIdents = ((EntryMeta) r).getTagIdents();
            latestInvalidation = Long.max(latestInvalidation, TagInvalidation.INSTANCE.lastInvalidatedEpoch(tagIdents));
        }
        if (epoch <= latestInvalidation) {
            RESULT.compareAndSet(this, null, EntryMeta.absent);
            return false;
        } else if (tagIdents != null && hasInvalidatedTagId(tagIdents)) {
            RESULT.compareAndSet(this, null, EntryMeta.absent);
            return false;
        }
        Object published = r == null ? NIL : r;
        if (!RESULT.compareAndSet(this, null, published)) {
            // A concurrent invalidate() won the race and set result=absent.
            Thread.interrupted();
            return false;
        }
        return true;
    }


    public void reject() {
        // Force absent regardless of any previously-published value. Used by the loader
        // after it has published the canonical CacheEntry to the delegate map, to redirect
        // joiners blocked in await() back through the map for revalidation.
        RESULT.set(this, EntryMeta.absent);
    }

    public void deliverException(Throwable t) {
        RESULT.compareAndSet(this, null, new AltResult(t));
    }

    public Object getNow() throws Throwable {
        Object r;
        if (d.getCount() != 0) {
            return EntryMeta.absent;
        }
        if ((r = result) instanceof AltResult) {
            Throwable x = ((AltResult) r).value;
            if (x == null) {
                return null;
            } else {
                throw x;
            }
        } else {
            return r == null ? EntryMeta.absent : r;
        }
    }

    public void invalidate() {
        // Invalidate always wins: clobber any prior value with absent so joiners blocked
        // in await() observe a miss and re-loop. The loader, if still running, will see
        // isInvalid()==true after its own deliver() and discard its entry. The caller of
        // invalidate is responsible for removing the entry from the delegate map.
        Object prev = RESULT.getAndSet(this, EntryMeta.absent);
        if (prev != EntryMeta.absent) {
            thread.interrupt();
        }
    }

    public boolean isInvalid() {
        return result == EntryMeta.absent;
    }

    public void releaseResult() {
        d.countDown();
    }

    public boolean hasInvalidatedTagId(IPersistentSet tagIdents) {
        if (tagIdents == null || tagIdents.count() == 0) {
            return false;
        }
        for (Object id : invalidatedIds) {
            if (tagIdents.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public void addInvalidIds(Iterable<Object> ids) {
        for (Object id : ids) {
            invalidatedIds.add(id);
        }
    }

    private static class AltResult {
        Throwable value;

        public AltResult(Throwable value) {
            this.value = value;
        }
    }
}
