package memento.caffeine;

import memento.base.CacheKey;

import java.util.concurrent.CountDownLatch;

/**
 * Special promise for use in indirection in Caffeine cache. Do not use otherwise.
 * <p>
 * This class is NOT threadsafe. The intended use is as a faster CompletableFuture that is
 * aware of the thread that made it, so it can detect when same thread is trying to await on it
 * and throw to prevent deadlock.
 *
 * It is also expected that it is created and delivered by the same thread and it is expected that
 * any other thread is awaiting result and that only a single attempt is made at delivering the result.
 *
 * It does not include logic to deal with multiple calls to deliver the promise, as it's optimized for
 * a specific use case.
 */
public class SpecialPromise implements Joinable {

    private static final AltResult NIL = new AltResult(null);
    private final CountDownLatch d = new CountDownLatch(1);
    // these 2 don't need to be thread-safe, because they are only used to check
    // if current thread is one that created and started the load on the promise
    // so even with non-volatile, check is only true if thread is same as current thread
    // so no memory barrier needed
    private CacheKey context;
    private Thread thread;
    private volatile Object result;

    public static Joinable completed(Object v) {
        return new Joinable() {
            @Override
            public Object join() {
                return v;
            }

            @Override
            public Object getNow(Object valueIfAbsent) {
                return v;
            }
        };
    }

    public void markLoadStart(CacheKey context) {
        this.thread = Thread.currentThread();
        this.context = context;
    }

    @Override
    public Object join() throws Throwable {
        Object r;
        if ((r = result) == null) {
            if (Thread.currentThread() == thread) {
                throw new StackOverflowError("Recursive load on key: " + context);
            } else {
                d.await();
                r = result;
            }
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

    public void deliver(Object r) {
        result = r == null ? NIL : r;
        context = null;
        thread = null;
        d.countDown();
    }

    public void deliverException(Throwable t) {
        result = new AltResult(t);
        context = null;
        thread = null;
        d.countDown();
    }

    public Object getNow(Object valueIfAbsent) throws Throwable {
        Object r;
        if ((r = result) instanceof AltResult) {
            Throwable x = ((AltResult) r).value;
            if (x == null) {
                return null;
            } else {
                throw x;
            }
        } else {
            return r == null ? valueIfAbsent : r;
        }
    }

    private static class AltResult {
        Throwable value;

        public AltResult(Throwable value) {
            this.value = value;
        }
    }

}