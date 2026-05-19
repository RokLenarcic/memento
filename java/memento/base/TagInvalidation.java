package memento.base;

import clojure.lang.IPersistentSet;
import clojure.lang.ISeq;
import clojure.lang.ITransientMap;
import clojure.lang.PersistentHashMap;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class represents a global map of ongoing bulk invalidations of Tag Ids. Adding listeners is used to enable implementations to
 * be able to communicate these lockouts outside the JVM.
 */
public class TagInvalidation {

    public static TagInvalidation INSTANCE = new TagInvalidation();

    private final AtomicReference<PersistentHashMap> m = new AtomicReference<>(PersistentHashMap.EMPTY);

    public TagInvalidation() {

    }

    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    /**
     * Add a new active invalidation for the given epoch.
     *
     * @param tagsAndIds
     * @return
     */
    public void startInvalidation(Iterable<Object> tagsAndIds, long epoch) {
        PersistentHashMap oldMap;
        PersistentHashMap newv;
        do {
            oldMap = m.get();
            ITransientMap newMap = oldMap.asTransient();
            for (Object e : tagsAndIds) {
                newMap.assoc(e, Long.max(epoch, (Long) oldMap.getOrDefault(e, InvalidationClock.NO_INVALIDATION_EPOCH)));
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while (!m.compareAndSet(oldMap, newv));
        for (Listener l : listeners) {
            l.startInvalidation(tagsAndIds, newv);
        }
    }

    public long lastInvalidatedEpoch(IPersistentSet tagsAndIds) {
        PersistentHashMap map = m.get();
        long ret = InvalidationClock.NO_INVALIDATION_EPOCH;
        if (tagsAndIds != null) {
            ISeq it = tagsAndIds.seq();
            while (it != null) {
                ret = Long.max((long)map.getOrDefault(it.first(), InvalidationClock.NO_INVALIDATION_EPOCH), ret);
                it = it.next();
            }
        }
        return ret;
    }

    /**
     * End active invalidation for keys and the epoch.
     *
     * @param tagsAndIds
     */
    public void endInvalidation(Iterable<Object> tagsAndIds, long epoch) {
        PersistentHashMap oldMap;
        PersistentHashMap newv;
        do {
            oldMap = m.get();
            ITransientMap newMap = oldMap.asTransient();
            for (Object e : tagsAndIds) {
                Object current = oldMap.get(e);
                if (current instanceof Long && ((Long) current) == epoch) {
                    newMap.without(e);
                }
            }
            newv = (PersistentHashMap) newMap.persistent();
        } while (!m.compareAndSet(oldMap, newv));
        for (Listener l : listeners) {
            l.endInvalidation(tagsAndIds, newv);
        }
    }

    public interface Listener {
        void startInvalidation(Iterable<Object> tagsAndIds, PersistentHashMap epochMap);

        void endInvalidation(Iterable<Object> tagsAndIds, PersistentHashMap epochMap);
    }
}
