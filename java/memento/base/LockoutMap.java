package memento.base;

import clojure.lang.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class LockoutMap {

    public static LockoutMap INSTANCE = new LockoutMap();

    private final AtomicReference<PersistentHashMap> m = new AtomicReference<>(PersistentHashMap.EMPTY);

    public LockoutMap() {

    }

    /**
     * Add a new lockout, returning the Latch, (if newer than existing) for the given keys
     * @param tagsAndIds
     * @return
     */
    public CountDownLatch startLockout(Iterable<Object> tagsAndIds) {
        CountDownLatch latch = new CountDownLatch(1);
        PersistentHashMap oldMap;
        PersistentHashMap newv;
        do {
            oldMap = m.get();
            ITransientMap newMap = oldMap.asTransient();
            for(Object e : tagsAndIds) {
                newMap.assoc(e, latch);
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while(!m.compareAndSet(oldMap, newv));
        return latch;
    }

    /**
     * End lockout for keys and the marker. After map is updated, marker's latch is released
     * @param tagsAndIds
     * @param latch
     */
    public void endInvalidation(Iterable<Object> tagsAndIds, CountDownLatch latch) {
        PersistentHashMap oldMap;
        PersistentHashMap newv;
        do {
            oldMap = m.get();
            ITransientMap newMap = oldMap.asTransient();
            for(Object e : tagsAndIds) {
                if (oldMap.get(e) == latch) {
                    newMap.without(e);
                }
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while(!m.compareAndSet(oldMap, newv));
        latch.countDown();
    }

    private boolean awaitMarker(PersistentHashMap lockouts, Object obj) throws InterruptedException {
        CountDownLatch latch = (CountDownLatch) lockouts.get(obj);
        if (latch != null) {
            latch.await();
            return true;
        } else {
            return false;
        }
    }

    /**
     * It awaits an invalidations to finish, returns after that. Returns true if the entry
     * was invalid and an invalidation was awaited.
     *
     */
    public boolean awaitLockout(Segment segment, Object promiseValue) throws InterruptedException {
        PersistentHashMap invalidations = m.get();
        if (invalidations.isEmpty()) {
            return false;
        }
        if (awaitMarker(invalidations, segment)) {
            return true;
        }
        if (promiseValue instanceof EntryMeta) {
            ISeq identSeq = ((EntryMeta) promiseValue).getTagIdents().seq();
            while (identSeq != null) {
                if (awaitMarker(invalidations, identSeq.first())) {
                    return true;
                }
                identSeq = identSeq.next();
            }
        }
        return false;
    }

}
