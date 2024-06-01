package memento.caffeine;

import clojure.lang.ISeq;
import memento.base.EntryMeta;
import memento.base.LockoutMap;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

/**
 * Special promise for use in indirection in Caffeine cache. Do not use otherwise.
 * <p>
 * This class is NOT threadsafe. The intended use is as a faster CompletableFuture that is
 * aware of the thread that made it, so it can detect when same thread is trying to await on it
 * and throw to prevent deadlock.
 * <p>
 * It is also expected that it is created and delivered by the same thread and it is expected that
 * any other thread is awaiting result and that only a single attempt is made at delivering the result.
 * <p>
 * It does not include logic to deal with multiple calls to deliver the promise, as it's optimized for
 * a specific use case.
 */
public class SpecialPromise {

    private static final AltResult NIL = new AltResult(null);
    private final CountDownLatch d = new CountDownLatch(1);
    // these 2 don't need to be thread-safe, because they are only used to check
    // if current thread is one that created and started the load on the promise
    // so even with non-volatile, check is only true if thread is same as current thread
    // so no memory barrier needed
    private final HashSet<Object> invalidatedIds = new HashSet<>();
    private volatile Thread thread;
    private volatile Object result;

    public void init() {
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

    private boolean isLockedOut(EntryMeta em) {
        try {
            return LockoutMap.awaitLockout(em);
        } catch (InterruptedException e) {
            return true;
        }
    }

    // Returns true if delivered object is viable
    public boolean deliver(Object r) {
        if (r instanceof EntryMeta) {
            EntryMeta em = (EntryMeta) r;
            if (isLockedOut(em) || hasInvalidatedTagId(em)) {
                result = EntryMeta.absent;
                return false;
            }
        }
        if (result != EntryMeta.absent) {
            result = r == null ? NIL : r;
            return true;
        }
        return false;
    }

    public void deliverException(Throwable t) {
        result = new AltResult(t);
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
        result = EntryMeta.absent;
        thread.interrupt();
    }

    public boolean isInvalid() {
        return result == EntryMeta.absent;
    }

    public void releaseResult() {
        d.countDown();
    }

    private boolean hasInvalidatedTagId(EntryMeta entryMeta) {
        synchronized (invalidatedIds) {
            ISeq s = entryMeta.getTagIdents().seq();
            while (s != null) {
                if (invalidatedIds.contains(s.first())) {
                    return true;
                }
                s = s.next();
            }
        }
        return false;
    }

    public void addInvalidIds(Iterable<Object> ids) {
        synchronized (invalidatedIds) {
            for (Object id : ids) {
                invalidatedIds.add(id);
            }
        }
    }

    private static class AltResult {
        Throwable value;

        public AltResult(Throwable value) {
            this.value = value;
        }
    }

}