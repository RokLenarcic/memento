# Internals

This document describes Memento's internal architecture. It's intended for contributors and those who want to understand how the library works under the hood.

## Architecture Overview

Memento has a layered architecture:

```
┌─────────────────────────────────────────┐
│           memento.core (API)            │  ← User-facing functions
├─────────────────────────────────────────┤
│  memento.mount (MountPoint)             │  ← Function ↔ Cache binding
├─────────────────────────────────────────┤
│  memento.caffeine (CaffeineCache)       │  ← Cache implementation
├─────────────────────────────────────────┤
│  Java classes (performance-critical)    │  ← Low-level operations
└─────────────────────────────────────────┘
```

## Key Concepts

### Cache vs MountPoint

**Cache** (`ICache`): Stores key-value pairs. One cache can serve multiple functions.

**MountPoint** (`IMountPoint`): Connects a function to a cache. Contains:
- Reference to the cache (direct or via tag lookup)
- Segment information (function metadata)
- Event handler

This separation enables:
1. Shared size limits across functions (one cache, multiple mount points)
2. Dynamic cache replacement via tags (mount point looks up cache at runtime)

### Segment

A `Segment` contains metadata about a memoized function binding:

```java
public class Segment {
    public final IFn f;       // Original function
    public final IFn keyFn;   // Key transformation function
    public final Object id;   // Identifier (typically var name)
    public final Object conf; // Mount configuration
}
```

### CacheKey

Cache entries are keyed by `CacheKey`, which combines the segment ID with transformed arguments:

```java
public class CacheKey {
    public final Object id;   // Segment identifier
    public final Object args; // Transformed function arguments
}
```

This allows multiple functions to share a cache while keeping their entries separate.

## Java vs Clojure Split

Performance-critical code is implemented in Java to:
1. **Reduce stack depth** for cached calls
2. **Minimize allocation** in hot paths
3. **Enable efficient concurrency primitives**

### Before Java Optimization (v1.0)
```
myns$myfn.invoke
clojure.lang.AFn.applyToHelper
clojure.lang.AFn.applyTo
clojure.core$apply.invokeStatic
clojure.core$apply.invoke
memento.caffeine.CaffeineCache$fn__2536.invoke
memento.caffeine.CaffeineCache.cached
memento.mount.UntaggedMountPoint.cached
memento.mount$bind$fn__2432.doInvoke
clojure.lang.RestFn.applyTo
clojure.lang.AFunction$1.doInvoke
clojure.lang.RestFn.invoke
```

### After Java Optimization (v1.1+)
```
myns$myfn.invoke
clojure.lang.AFn.applyToHelper
memento.caffeine.CaffeineCache$fn__2052.invoke
memento.caffeine.CaffeineCache.cached
memento.mount.CachedFn.invoke
```

From 11 stack frames to 4.

## Java Classes

### `memento.base`

- **`ICache`**: Core cache interface with methods like `cached`, `invalidate`, `addEntries`
- **`Segment`**: Function binding metadata
- **`CacheKey`**: Composite key (id + args)
- **`EntryMeta`**: Wrapper for cached values with metadata (tag IDs, no-cache flag)
- **`Durations`**: Time unit conversions

### `memento.mount`

- **`IMountPoint`**: Interface for mount points
- **`Cached`**: Marker interface for memoized functions
- **`CachedFn`**: IFn implementation that delegates to mount point
- **`CachedMultiFn`**: MultiFn wrapper for memoized multimethods

### `memento.caffeine`

- **`CaffeineCache_`**: Core Caffeine operations
- **`SecondaryIndex`**: Maps tag+ID pairs to cache keys and coordinates Caffeine invalidation epochs
- **`Expiry`**: Interface for variable per-entry expiry
- **`SpecialPromise`**: Promise that tracks invalidation state during loads

### `memento.multi`

- **`MultiCache`**: Base class for tiered caches
- **`TieredCache`**: Both caches updated on miss
- **`ConsultingCache`**: Only local updated on miss
- **`DaisyChainCache`**: Local never updated

## Concurrency Handling

### Single Load Per Key

Caffeine ensures only one load happens per key. If multiple threads request the same uncached key simultaneously:
1. First thread starts the load
2. Other threads wait on a `CompletableFuture`
3. When load completes, all threads get the result

### Invalidation During Load

If a key is invalidated while being loaded:

1. **For single-key invalidation**: The `SpecialPromise` is marked invalid
2. Load completes but result is discarded
3. Cache retries the load with fresh data

### Tag-Based Invalidation

Tag invalidation is more complex because:
- Multiple keys may be affected
- Ongoing loads may produce stale data
- We need atomicity across multiple operations

#### Invalidation Sequence

1. Invoke every backend's lightweight `start-secondary-invalidation!` method.
2. Invoke every backend's `invalidate-secondary!` method, passing start state.
3. Invoke every backend's `end-secondary-invalidation!` method, passing the state
   returned by invalidation (or start state when invalidation failed).

Secondary-index invalidation is backend-owned and does not enumerate mount points. The
Caffeine backend uses one JVM-wide index containing weak cache/key references and local
epochs. Distributed backends can use native indexes and backend-specific epochs. Starting
all backends before running any potentially slow invalidator gives their lockout windows the
widest practical overlap; cross-backend atomicity is not implied.

#### Load Sequence (with tag checking)

1. Publish a `SpecialPromise` for the key and capture the current `InvalidationClock`
   value as the candidate load's epoch.
2. If this thread published the promise, register the load with the secondary index and
   invoke the cached function.
3. Before publishing the result, compare the load's epoch with the segment and
   cache invalidation epochs. If the load predates either, discard it and retry.
4. Otherwise CAS-publish the value onto the `SpecialPromise` via `deliver`.
   Concurrent `invalidate()` calls also CAS the promise to `EntryMeta.absent`;
   whichever wins determines the outcome. If `deliver` lost the race, the loader
   removes the entry from the delegate map and retries.
5. After `deliver` wins, `finishLoad` checks active tag invalidation epochs, tag IDs
   recorded on the promise, and whether the promise was directly invalidated after
   delivery. If valid, it publishes the canonical `CacheEntry`; otherwise the loader
   rejects the delivered result and retries.
6. Callers already waiting on a successfully published promise receive its result
   directly; later callers read the `CacheEntry` from the map.

Load registration intentionally happens after winning `putIfAbsent` and immediately before
invoking the cached function. A tag invalidation can therefore complete between promise
publication and registration without marking the promise. This is treated as the load
starting after that invalidation because no user computation has begun yet. Registering
before publication would close that internal handoff window, but would require an extra map
lookup on misses and more complex ownership cleanup without protecting against the external
gap between mutating the underlying data and calling invalidation. Once function invocation
begins, the registration and epoch checks prevent an overlapping invalidation from allowing
the load to publish stale data.

There is a narrow publication-boundary gap: after the promise has been replaced by a
`CacheEntry`, an invalidation can remove that entry while a caller already holding the
detached, successfully delivered promise still returns its value. The loader can likewise
return its computed value after concurrent removal. Memento detects invalidations during
meaningful load execution through epochs and promise invalidation, but does not attempt to
close this final handoff race. Closing it would require successful joiners to re-read the
map while still not providing an absolute guarantee for the loader itself.

`memento.core/memo-clear-tags!` orchestrates the three backend lifecycle phases. Core does
not create or interpret invalidation epochs.

#### Promise Result CAS

`SpecialPromise.result` is updated through an `AtomicReferenceFieldUpdater`.
The transitions are:

- `deliver` / `deliverException`: CAS from `null` to a published value. Fails
  if another writer (typically `invalidate`) already moved the field.
- `invalidate`: `getAndSet` to `EntryMeta.absent`. Always wins; only interrupts
  the loader thread if it observed a non-`absent` prior value (i.e. it actually
  clobbered something, ensuring the interrupt has a meaningful target).
- `reject`: unconditional `set` to `EntryMeta.absent`. Used when validation or
  publication fails after `deliver`, preventing joiners from observing a result
  that the loader discarded.

### Thread Interruption

When a tag is invalidated while a load is in progress for an entry with that tag:
- The loading thread is interrupted
- This allows long-running loads to abort early
- The load will be retried after invalidation completes

## Secondary Index

The `SecondaryIndex` maintains mappings from tag+ID pairs to cache keys:

```
Tag: :user
  ID: 123 -> #{CacheKey[get-user, [123]], CacheKey[get-orders, [123]]}
  ID: 456 -> #{CacheKey[get-user, [456]]}

Tag: :order
  ID: 789 -> #{CacheKey[get-order, [789]], CacheKey[get-order-items, [789]]}
```

When `memo-clear-tag!` is called:
1. Look up all cache keys for the tag+ID
2. Invalidate each key in the cache
3. Remove the mapping from the index

### EntryMeta

Cached values are wrapped in `EntryMeta` which tracks:
- The actual value
- Whether to cache (`noCache` flag from `do-not-cache`)
- Set of tag+ID pairs (for secondary index)

```java
public class EntryMeta {
    public final Object v;           // The cached value
    public final boolean noCache;    // If true, don't cache this
    public final Set tagIdents;      // Set of [tag, id] pairs
}
```

## Reload Guards

In development, namespaces are frequently reloaded. When a memoized function's var is redefined:
1. Old function still exists (with its mount point)
2. New function is created (with new mount point)
3. Tag mappings for old function become stale

Reload guards use Java finalizers to clean up:
- When old memoized function is GCed
- Its mount point is removed from tag mappings
- This prevents memory leaks and stale references

Disable for production: `-Dmemento.reloadable=false`

## Cache Lifecycle

### Creation

```clojure
(m/create {mc/ttl [5 :m]})
```

1. `memento.base/new-cache` multimethod dispatches on `mc/type`
2. For Caffeine: builds `Caffeine` instance with configuration
3. Wraps in `CaffeineCache` record implementing `ICache`

### Binding

```clojure
(m/bind #'get-user {} my-cache)
```

1. Creates `Segment` with function, key-fn, id, config
2. Creates mount point (Tagged or Untagged based on tags)
3. Wraps original function in `CachedFn`
4. Alters var root to the wrapped function
5. Registers mount point with tag mappings (if tagged)

### Cache Hit

```clojure
(get-user 123)
```

1. `CachedFn.invoke` called with args
2. Delegates to `IMountPoint.cached`
3. Mount point resolves actual cache (may involve tag lookup)
4. Creates `CacheKey` from segment ID + transformed args
5. Caffeine lookup (hit) - returns value
6. Applies `ret-fn` if configured
7. Returns to caller

### Cache Miss

1. Steps 1-4 same as hit
2. Caffeine lookup (miss) - triggers load function
3. Load function calls original function with args
4. Result wrapped in `EntryMeta`
5. Applies `ret-fn`, extracts tag IDs
6. If `noCache` flag set, returns without caching
7. Otherwise stores in cache, updates secondary index
8. Returns to caller

## Extending Memento

### Custom Cache Implementation

Implement `memento.base/ICache`:

```clojure
(defrecord MyCache [...]
  ICache
  (conf [this] ...)
  (cached [this segment args] ...)
  (ifCached [this segment args] ...)
  (invalidate [this segment] ...)
  (invalidate [this segment args] ...)
  (invalidateAll [this] ...)
  (addEntries [this segment args-to-vals] ...)
  (asMap [this] ...)
  (asMap [this segment] ...))
```

Register with multimethod:

```clojure
(defmethod memento.base/new-cache :my-cache-type
  [conf]
  (->MyCache ...))

(defmethod memento.base/start-secondary-invalidation! :my-cache-type
  [_ tag-ids]
  ;; Establish a lightweight lockout and return backend-specific state.
  ...)

(defmethod memento.base/invalidate-secondary! :my-cache-type
  [_ tag-ids state]
  ;; Invalidate active storage domains and return state for the end phase.
  state)

(defmethod memento.base/end-secondary-invalidation! :my-cache-type
  [_ tag-ids state]
  ;; Release the backend lockout.
  nil)
```

Use:

```clojure
(m/memo my-fn {mc/type :my-cache-type ...})
```
