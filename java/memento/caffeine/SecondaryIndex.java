package memento.caffeine;

import clojure.lang.ISeq;
import clojure.lang.IPersistentSet;
import memento.base.CacheEntry;
import memento.base.CacheKey;
import memento.base.EntryMeta;
import memento.base.InvalidationTimeline;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** JVM-wide secondary index for Caffeine cache instances. */
public final class SecondaryIndex {

    public static final SecondaryIndex INSTANCE = new SecondaryIndex();

    private final ConcurrentHashMap<Object, Set<IndexEntry>> lookup = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> evicted = new ReferenceQueue<>();
    private final InvalidationTimeline timeline = new InvalidationTimeline();

    private SecondaryIndex() {}

    void add(CaffeineCache_ cache, CacheKey key, CacheEntry entry) {
        ensureCleanerStarted();
        ISeq ids = entry.getSecIds().seq();
        while (ids != null) {
            Object id = ids.first();
            lookup.compute(id, (ignored, current) -> {
                Set<IndexEntry> entries = current == null ? ConcurrentHashMap.newKeySet() : current;
                entries.add(new IndexEntry(id, entries, cache, key, entry.getWriteEpoch(), evicted));
                return entries;
            });
            ids = ids.next();
        }
    }

    public InvalidationTimeline.Operation startOperation() {
        return timeline.startOperation();
    }

    public boolean isInvalid(InvalidationTimeline.Operation start, EntryMeta object) {
        return timeline.invalidated(start, (Set<?>) object.getSecIds());
    }

    public boolean isInvalid(InvalidationTimeline.Operation start, CacheEntry object) {
        return timeline.invalidated(start, (Set<?>) object.getSecIds());
    }

    public InvalidationTimeline.Invalidation startInvalidation(Iterable<?> ids) {
        return timeline.startInvalidation(ids);
    }

    public void invalidate(InvalidationTimeline.Invalidation invalidation) {
        for (Object id : invalidation.ids()) {
            Set<IndexEntry> entries = lookup.remove(id);
            if (entries != null) {
                for (IndexEntry entry : entries) {
                    CaffeineCache_ cache = entry.cache();
                    CacheKey key = entry.key();
                    if (cache != null && key != null) {
                        cache.invalidateIndexed(key, entry.writeEpoch());
                    }
                }
            }
        }
    }

    public void endInvalidation(InvalidationTimeline.Invalidation invalidation) {
        timeline.endInvalidation(invalidation);
    }

    public boolean hasActiveInvalidation(IPersistentSet ids) {
        return timeline.hasActiveInvalidation((Set<?>) ids);
    }

    void removeKeys(CaffeineCache_ cache, CacheKey key, CacheEntry entry) {
        ISeq ids = entry.getSecIds().seq();
        while (ids != null) {
            Object id = ids.first();
            lookup.computeIfPresent(id, (ignored, entries) -> {
                    entries.remove(new IndexEntry(id, entries, cache, key, entry.getWriteEpoch(), null));
                    return entries.isEmpty() ? null : entries;
                });
            ids = ids.next();
        }
    }

    private final class IndexEntry {
        private final Object id;
        private final Set<IndexEntry> home;
        private final WeakReference<CaffeineCache_> cache;
        private final WeakReference<CacheKey> key;
        private final int hash;
        private final long writeEpoch;

        private IndexEntry(Object id, Set<IndexEntry> home, CaffeineCache_ cache, CacheKey key, long writeEpoch,
                           ReferenceQueue<Object> queue) {
            this.id = id;
            this.home = home;
            this.cache = tracked(cache, queue, this::delete);
            this.key = tracked(key, queue, this::delete);
            this.hash = Objects.hash(System.identityHashCode(cache), key, writeEpoch);
            this.writeEpoch = writeEpoch;
        }

        private CaffeineCache_ cache() {
            return cache.get();
        }

        private CacheKey key() {
            return key.get();
        }

        private long writeEpoch() {
            return writeEpoch;
        }

        private void delete() {
            home.remove(this);
            if (home.isEmpty()) {
                lookup.computeIfPresent(id, (ignored, entries) ->
                        entries == home && entries.isEmpty() ? null : entries);
            }
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SecondaryIndex.IndexEntry)) {
                return false;
            }
            IndexEntry that = (IndexEntry) other;
            CaffeineCache_ cache = cache();
            return cache != null && writeEpoch == that.writeEpoch
                   && cache == that.cache() && Objects.equals(key(), that.key());
        }
    }

    private static volatile boolean cleanerStarted;

    private <T> WeakReference<T> tracked(T value, ReferenceQueue<Object> queue, Runnable cleanup) {
        if (queue == null) {
            return new WeakReference<>(value);
        }
        return new TrackedReference<>(value, queue, cleanup);
    }

    private static final class TrackedReference<T> extends WeakReference<T> {
        private final Runnable cleanup;

        private TrackedReference(T value, ReferenceQueue<Object> queue, Runnable cleanup) {
            super(value, queue);
            this.cleanup = cleanup;
        }

        private void delete() {
            cleanup.run();
        }
    }

    private static void ensureCleanerStarted() {
        if (!cleanerStarted) {
            synchronized (SecondaryIndex.class) {
                if (!cleanerStarted) {
                    cleanerStarted = true;
                    Thread cleaner = new Thread(INSTANCE::clean, "Memento Caffeine Sec Idx Cleaner");
                    cleaner.setDaemon(true);
                    cleaner.start();
                }
            }
        }
    }

    private void clean() {
        while (true) {
            try {
                ((TrackedReference<?>) evicted.remove()).delete();
            } catch (InterruptedException ignored) {
                // Keep the process-wide cleaner alive.
            }
        }
    }
}
