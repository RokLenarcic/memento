package memento.base;

import clojure.lang.IPersistentSet;
import clojure.lang.ISeq;
import clojure.lang.ITransientMap;
import clojure.lang.PersistentHashMap;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class represents a global map of ongoing bulk invalidations of Tag Ids. Await lockout can be used
 * to await for bulk invalidation to finish. Adding listeners is used to enable implementations to
 * be able to communicate these lockouts outside the JVM.
 */
public class LockoutMap {

    public static LockoutMap INSTANCE = new LockoutMap();

    private final AtomicReference<PersistentHashMap> m = new AtomicReference<>(PersistentHashMap.EMPTY);

    public LockoutMap() {

    }

    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    /**
     * Add a new lockout, returning the Latch, (if newer than existing) for the given keys
     *
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
            for (Object e : tagsAndIds) {
                newMap.assoc(e, latch);
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while (!m.compareAndSet(oldMap, newv));
        listeners.forEach(l -> l.startLockout(tagsAndIds, latch));
        return latch;
    }

    /**
     * End lockout for keys and the marker. After map is updated, marker's latch is released
     *
     * @param tagsAndIds
     * @param latch
     */
    public void endLockout(Iterable<Object> tagsAndIds, CountDownLatch latch) {
        PersistentHashMap oldMap;
        PersistentHashMap newv;
        do {
            oldMap = m.get();
            ITransientMap newMap = oldMap.asTransient();
            for (Object e : tagsAndIds) {
                if (oldMap.get(e) == latch) {
                    newMap.without(e);
                }
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while (!m.compareAndSet(oldMap, newv));
        listeners.forEach(l -> l.endLockout(tagsAndIds, latch));
        latch.countDown();
    }

    private static boolean awaitMarker(PersistentHashMap lockouts, Object obj) throws InterruptedException {
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
     */
    public static boolean awaitLockout(Object promiseValue) throws InterruptedException {
        if (promiseValue instanceof EntryMeta) {
            IPersistentSet idents = ((EntryMeta) promiseValue).getTagIdents();
            if (idents.count() != 0) {
                PersistentHashMap invalidations = LockoutMap.INSTANCE.m.get();
                if (invalidations.isEmpty()) {
                    return false;
                }
                ISeq identSeq = ((EntryMeta) promiseValue).getTagIdents().seq();
                boolean ret = false;
                while (identSeq != null) {
                    ret |= awaitMarker(invalidations, identSeq.first());
                    identSeq = identSeq.next();
                }
                return ret;
            }
        }
        return false;
    }

    public interface Listener {
        void startLockout(Iterable<Object> tagsAndIds, CountDownLatch latch);

        void endLockout(Iterable<Object> tagsAndIds, CountDownLatch latch);
    }
}
