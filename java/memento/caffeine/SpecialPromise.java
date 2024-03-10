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

    @Override
    public Object join() throws Throwable {
        Object r;
        if ((r = result) == null) {
            if (Thread.currentThread() == thread) {
                throw new StackOverflowError("Cache loader recurses on itself: " + context);
            } else {
                d.await();
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

    public SpecialPromise(CacheKey context) {
        this.context = context;
    }

    private static class AltResult {
        Throwable value;

        public AltResult(Throwable value) {
            this.value = value;
        }
    }

    private static final AltResult NIL = new AltResult(null);

    private final Thread thread = Thread.currentThread();

    private CacheKey context;

    private volatile Object result;
    private final CountDownLatch d = new CountDownLatch(1);
    public void deliver(Object r) {
        result = r == null ? NIL : r;
        context = null;
        d.countDown();
    }

    public void deliverException(Throwable t) {
        result = new AltResult(t);
        context = null;
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

}