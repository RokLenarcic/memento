package memento.caffeine;

import clojure.lang.ISeq;
import memento.base.CacheEntry;
import memento.base.CacheKey;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class SecondaryIndex {

    private final ConcurrentHashMap<Object, Set<IndexEntry>> lookup;

    public SecondaryIndex(int concurrency) {
        ensureCleanerStarted();
        this.lookup = new ConcurrentHashMap<>(16, 0.75f, concurrency);
    }

    /**
     * Add entry to secondary index.
     * k is CacheKey of incoming Cache entry
     * entry is the incoming cache entry. For each tag ID we add CacheKey to its HashSet.
     * <p>
     * For each ID we add CacheKey to its HashSet.
     *
     * @param k
     * @param v
     */
    public void add(CacheKey k, CacheEntry entry) {
        ISeq s = entry.getTagIdents().seq();
        while (s != null) {
            Set<IndexEntry> cacheKeys = lookup.computeIfAbsent(s.first(), key -> new HashSet<>());
            synchronized (cacheKeys) {
                cacheKeys.add(new IndexEntry(cacheKeys, k, entry.getWriteEpoch()));
            }
            s = s.next();
        }
    }

    public void removeIf(Object tagId, Predicate<IndexEntry> shouldRemove) {
        Set<IndexEntry> entries = lookup.get(tagId);
        if (entries != null) {
            synchronized (entries) {
                Iterator<IndexEntry> iter = entries.iterator();
                while (iter.hasNext()) {
                    IndexEntry entry = iter.next();
                    if (entry.getKey() == null || shouldRemove.test(entry)) {
                        iter.remove();
                    }
                }
                if (entries.isEmpty()) {
                    lookup.remove(tagId, entries);
                }
            }
        }
    }

    public void removeKeys(CacheKey key, CacheEntry entry) {
        ISeq tagIds = entry.getTagIdents().seq();
        while (tagIds != null) {
            Object tagId = tagIds.first();
            Set<IndexEntry> entries = lookup.get(tagId);
            if (entries != null) {
                synchronized (entries) {
                    entries.remove(new IndexEntry(entries, key, entry.getWriteEpoch()));
                    if (entries.isEmpty()) {
                        lookup.remove(tagId, entries);
                    }
                }
            }
            tagIds = tagIds.next();
        }
    }


    private static final ReferenceQueue<CacheKey> evicted = new ReferenceQueue<>();

    public static class IndexEntry extends WeakReference<CacheKey> {
        private final Set<IndexEntry> home;
        private final int hash;
        private final long writeEpoch;

        public IndexEntry(Set<IndexEntry> home, CacheKey key, long writeEpoch) {
            super(key, evicted);
            this.hash = Objects.hash(key, writeEpoch);
            this.home = home;
            this.writeEpoch = writeEpoch;
        }

        public CacheKey getKey() {
            return get();
        }

        public long getWriteEpoch() {
            return writeEpoch;
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
                return hash == that.hash && writeEpoch == that.writeEpoch && Objects.equals(get(), that.get());
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

    private static volatile Thread cleanerThread;
    private static final AtomicBoolean cleanerStarted = new AtomicBoolean(false);

    public static void ensureCleanerStarted() {
        if (cleanerStarted.compareAndSet(false, true)) {
            cleanerThread = new Thread(new Cleaner(), "Memento Secondary Index Cleaner");
            cleanerThread.setDaemon(true);
            cleanerThread.start();
        }
    }


}
