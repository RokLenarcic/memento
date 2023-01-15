package memento.caffeine;

import clojure.lang.IFn;
import clojure.lang.ISeq;
import com.github.benmanes.caffeine.cache.RemovalListener;
import memento.base.EntryMeta;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SecondaryIndexOps {

    /**
     * Remove value from secondary index, processing EntityMeta if there is one.
     * <p>
     * v is value of Cache entry, might be EntryMeta, if it is then we use each of tag-idents as key (id)
     * pointing to a HashSet. We remove CacheKey from each set, removing the whole entry if the resulting
     * set is empty.
     *
     * @param secIndex
     * @param k        CacheKey of Cache entry being removed
     * @param v
     */
    public static void secIndexDisj(ConcurrentHashMap<Object, Set<Object>> secIndex, Object k, Object v) {
        if (v instanceof EntryMeta) {
            EntryMeta e = ((EntryMeta) v);
            ISeq s = e.getTagIdents().seq();
            while (s != null) {
                secIndex.computeIfPresent(s.first(), (i, hashSet) -> {
                    hashSet.remove(k);
                    return hashSet.isEmpty() ? null : hashSet;
                });
                s = s.next();
            }
        }
    }

    /**
     * Add entry to secondary index.
     * k is CacheKey of incoming Cache entry
     * v is value of incoming cache entry, might be EntryMeta, if it is then we use each tag-idents
     * as key (id) pointing to a HashSet of CacheKeys.
     * <p>
     * For each ID we add CacheKey to its HashSet.
     *
     * @param secIndex
     * @param k
     * @param v
     */
    public static void secIndexConj(ConcurrentHashMap<Object, Set<Object>> secIndex, Object k, Object v) {
        if (v instanceof EntryMeta) {
            EntryMeta e = ((EntryMeta) v);
            ISeq s = e.getTagIdents().seq();
            while (s != null) {
                secIndex.compute(s.first(), (i, hashSet) -> {
                    Set<Object> set = hashSet == null ? new HashSet<>() : hashSet;
                    set.add(k);
                    return set;
                });
                s = s.next();
            }
        }
    }

    public static RemovalListener<CacheKey, Object> listener(ConcurrentHashMap<Object, Set<Object>> secIndex) {
        return (k, v, removalCause) -> {
            secIndexDisj(secIndex, k, v);
        };
    }

    public static RemovalListener<CacheKey, Object> listener(ConcurrentHashMap<Object, Set<Object>> secIndex, IFn removalListener) {
        return (k, v, removalCause) -> {
            secIndexDisj(secIndex, k, v);
            removalListener.invoke(k.getId(), k.getArgs(), v instanceof EntryMeta ? ((EntryMeta) v).getV() : v, removalCause);
        };
    }

}
