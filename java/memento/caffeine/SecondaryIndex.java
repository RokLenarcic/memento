package memento.caffeine;

import clojure.lang.ISeq;
import clojure.lang.IPersistentSet;
import clojure.lang.ITransientMap;
import clojure.lang.PersistentHashMap;
import memento.base.CacheEntry;
import memento.base.CacheKey;
import memento.base.InvalidationClock;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** JVM-wide secondary index for Caffeine cache instances. */
public final class SecondaryIndex {

    public static final SecondaryIndex INSTANCE = new SecondaryIndex();

    private final ConcurrentHashMap<Object, Set<IndexEntry>> lookup = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> evicted = new ReferenceQueue<>();
    private final Set<LoadEntry> loads = ConcurrentHashMap.newKeySet();
    private final AtomicReference<PersistentHashMap> activeInvalidations =
            new AtomicReference<>(PersistentHashMap.EMPTY);

    private SecondaryIndex() {}

    public void add(CaffeineCache_ cache, CacheKey key, CacheEntry entry) {
        ensureCleanerStarted();
        ISeq ids = entry.getTagIdents().seq();
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

    public LoadEntry addLoad(CaffeineCache_ cache, SpecialPromise promise) {
        ensureCleanerStarted();
        LoadEntry load = new LoadEntry(cache, promise, evicted);
        loads.add(load);
        return load;
    }

    public void removeLoad(LoadEntry load) {
        loads.remove(load);
    }

    public boolean finishLoad(LoadEntry load, IPersistentSet ids, BooleanSupplier publish) {
        SpecialPromise promise = load.promise();
        boolean valid = promise != null
                        && lastInvalidatedEpoch(ids) == InvalidationClock.NO_INVALIDATION_EPOCH
                        && !promise.isInvalid()
                        && !promise.hasInvalidatedTagId(ids);
        return valid && publish.getAsBoolean();
    }

    public long startInvalidation(Iterable<Object> ids) {
        long epoch = InvalidationClock.claimInvalidationEpoch();
        updateInvalidations(ids, epochs -> Epochs.insert(epochs, epoch));
        return epoch;
    }

    public void invalidate(Iterable<Object> ids, long epoch) {
        for (Object id : ids) {
            lookup.computeIfPresent(id, (ignored, entries) -> {
                Iterator<IndexEntry> iterator = entries.iterator();
                while (iterator.hasNext()) {
                    IndexEntry entry = iterator.next();
                    if (entry.cache() == null || entry.key() == null || invalidateEntry(epoch, entry)) {
                        iterator.remove();
                    }
                }
                return entries.isEmpty() ? null : entries;
            });
        }
    }

    public void endInvalidation(Iterable<Object> ids, long epoch) {
        // ids is immutable sequence
        for (LoadEntry load : loads) {
            SpecialPromise promise = load.promise();
            CaffeineCache_ cache = load.cache();
            if (promise == null || cache == null) {
                loads.remove(load);
            } else {
                promise.addInvalidIds(ids);
            }
        }
        updateInvalidations(ids, epochs -> Epochs.remove(epochs, epoch));
    }

    public long lastInvalidatedEpoch(IPersistentSet ids) {
        PersistentHashMap snapshot = activeInvalidations.get();
        long latest = InvalidationClock.NO_INVALIDATION_EPOCH;
        if (ids != null) {
            for (ISeq seq = ids.seq(); seq != null; seq = seq.next()) {
                Epochs epochs = (Epochs) snapshot.get(seq.first());
                if (epochs != null) {
                    latest = Long.max(latest, epochs.epoch);
                }
            }
        }
        return latest;
    }

    private void updateInvalidations(Iterable<Object> ids, Function<Epochs, Epochs> operation) {
        PersistentHashMap oldMap;
        PersistentHashMap newMap;
        do {
            oldMap = activeInvalidations.get();
            ITransientMap transientMap = oldMap.asTransient();
            for (Object id : ids) {
                Epochs updated = operation.apply((Epochs) oldMap.get(id));
                if (updated == null) {
                    transientMap.without(id);
                } else {
                    transientMap.assoc(id, updated);
                }
            }
            newMap = (PersistentHashMap) transientMap.persistent();
        } while (!activeInvalidations.compareAndSet(oldMap, newMap));
    }

    private boolean invalidateEntry(long epoch, IndexEntry entry) {
        CaffeineCache_ cache = entry.cache();
        CacheKey key = entry.key();
        if (cache == null || key == null) {
            return true;
        }
        return cache.invalidateIndexed(key, entry.writeEpoch(), epoch);
    }

    public void removeKeys(CaffeineCache_ cache, CacheKey key, CacheEntry entry) {
        ISeq ids = entry.getTagIdents().seq();
        while (ids != null) {
            Object id = ids.first();
            lookup.computeIfPresent(id, (ignored, entries) -> {
                    entries.remove(new IndexEntry(id, entries, cache, key, entry.getWriteEpoch(), null));
                    return entries.isEmpty() ? null : entries;
                });
            ids = ids.next();
        }
    }

    private static final class Epochs {
        private final long epoch;
        private final Epochs tail;

        private Epochs(long epoch, Epochs tail) {
            this.epoch = epoch;
            this.tail = tail;
        }

        private static Epochs insert(Epochs epochs, long epoch) {
            if (epochs == null || epoch >= epochs.epoch) {
                return new Epochs(epoch, epochs);
            }
            return new Epochs(epochs.epoch, insert(epochs.tail, epoch));
        }

        private static Epochs remove(Epochs epochs, long epoch) {
            if (epochs == null || epochs.epoch < epoch) {
                return epochs;
            }
            if (epochs.epoch == epoch) {
                return epochs.tail;
            }
            Epochs newTail = remove(epochs.tail, epoch);
            return newTail == epochs.tail ? epochs : new Epochs(epochs.epoch, newTail);
        }
    }

    public final class IndexEntry {
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

    public final class LoadEntry {
        private final WeakReference<CaffeineCache_> cache;
        private final WeakReference<SpecialPromise> promise;

        private LoadEntry(CaffeineCache_ cache, SpecialPromise promise, ReferenceQueue<Object> queue) {
            this.cache = tracked(cache, queue, this::delete);
            this.promise = tracked(promise, queue, this::delete);
        }

        private CaffeineCache_ cache() {
            return cache.get();
        }

        private SpecialPromise promise() {
            return promise.get();
        }

        private void delete() {
            loads.remove(this);
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
