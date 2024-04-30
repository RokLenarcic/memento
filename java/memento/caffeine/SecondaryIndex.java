package memento.caffeine;

import clojure.lang.ISeq;
import memento.base.CacheKey;
import memento.base.EntryMeta;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SecondaryIndex {

    private final ConcurrentHashMap<Object, Set<IndexEntry>> lookup;

    public SecondaryIndex(int concurrency) {
        this.lookup = new ConcurrentHashMap<>(16, 0.75f, concurrency);
    }

    /**
     * Add entry to secondary index.
     * k is CacheKey of incoming Cache entry
     * v is value of incoming cache entry, might be EntryMeta, if it is then we use each tag-idents
     * as key (id) pointing to a HashSet of CacheKeys.
     * <p>
     * For each ID we add CacheKey to its HashSet.
     *
     * @param k
     * @param v
     */
    public void add(CacheKey k, Object v) {
        if (v instanceof EntryMeta) {
            EntryMeta e = ((EntryMeta) v);
            ISeq s = e.getTagIdents().seq();
            while (s != null) {
                Set<IndexEntry> cacheKeys = lookup.computeIfAbsent(s.first(), key -> new HashSet<>());
                synchronized (cacheKeys) {
                    cacheKeys.add(new IndexEntry(cacheKeys, k));
                }
                s = s.next();
            }
        }
    }

    public void drainKeys(Object tagId, Consumer<CacheKey> onValue) {
        Set<IndexEntry> entries = lookup.remove(tagId);
        if (entries != null) {
            synchronized (entries) {
                for (IndexEntry e : entries) {
                    CacheKey c = e.get();
                    if (c != null) {
                        onValue.accept(c);
                    }
                }
            }
        }
    }


    private static final ReferenceQueue<CacheKey> evicted = new ReferenceQueue<>();

    public static class IndexEntry extends WeakReference<CacheKey> {
        private final Set<IndexEntry> home;
        private final int hash;

        public IndexEntry(Set<IndexEntry> home, CacheKey key) {
            super(key, evicted);
            this.hash = key.hashCode();
            this.home = home;
        }

        public void delete() {
            synchronized (home) {
                home.remove(this);
            }
        }

        @Override
        // this is only used when adding entries, so we can expect this to have the underlying key here
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof IndexEntry) {
                IndexEntry that = (IndexEntry) o;
                return hash == that.hash && Objects.equals(get(), that.get());
            } else {
                return false;
            }
        }

        @Override
        // this is special hashcode, it remembers the key object's hash so we can kinda use hashset
        public int hashCode() {
            return hash;
        }
    }

    public static class Cleaner implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    IndexEntry ref = (IndexEntry) evicted.remove();
                    ref.delete();
                } catch (InterruptedException e) {
                    //
                }
            }
        }
    }

    public static final Thread cleanerThread = new Thread(new Cleaner(), "Memento Secondary Index Cleaner");

    static {
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }


}
