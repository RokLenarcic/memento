# Scoped Caching Guide

This guide explains how to use Memento's scoped caching feature. While request handling is the most common use case, scopes can be used for any bounded context - background jobs, batch processing, test fixtures, etc. Scopes can also be nested.

## Why Traditional Caching Falls Short for APIs

When building APIs or web applications, traditional caching approaches have fundamental problems:

### TTL-Based Caching Doesn't Work Well

With TTL (time-to-live) caching, you must choose an expiration time. But what's the right value?

- **Too short** (e.g., 5 seconds): You barely get any cache hits. Most requests still hit the database.
- **Too long** (e.g., 5 minutes): Users see stale data. Someone updates their profile, but the old version keeps appearing.

The core problem is that **data can change at any moment**. There's no "safe" TTL that balances freshness and performance. You're always making a tradeoff, and often getting it wrong in one direction or the other.

### Size-Based Caching Doesn't Help Staleness

Size limits (LRU eviction) prevent memory from growing unbounded, but they don't address staleness at all. A cached entry could be:
- Seconds old (fresh)
- Hours old (very stale)

You have no guarantees. Size-based caching is about memory management, not data freshness.

### What You Actually Want

For request handling, the ideal caching behavior is:

1. **Fresh start**: Each request begins with no cached data (or only data you explicitly trust)
2. **Cache within request**: If the same data is needed multiple times during a request, fetch it once
3. **Automatic cleanup**: When the request ends, the cache is discarded

This gives you the performance benefit of caching (no repeated DB calls within a request) without the staleness problem (each request sees current data).

## The Solution: `with-caches`

Memento's `with-caches` macro temporarily replaces caches for tagged functions within a scope.

```clojure
(m/with-caches :tag-name cache-factory-fn
  body...)
```

The `cache-factory-fn` is called **for each tagged mount point** with its current cache as the argument. The returned cache is used for that function within the scope. Most commonly, you use `constantly` to share one cache across all tagged functions:

```clojure
;; All :request-tagged functions share this one cache
(m/with-caches :request
  (constantly (m/create {mc/type mc/caffeine}))
  (handle-request request))
```

But you can also create per-function caches or make decisions based on the existing cache:

```clojure
;; Each tagged function gets its own cache with a size limit
(m/with-caches :request
  (fn [existing-cache] (m/create {mc/type mc/caffeine mc/size< 100}))
  (handle-request request))

;; Wrap existing cache with consulting pattern
(m/with-caches :request
  (fn [existing-cache] 
    (m/create (m/consulting {mc/type mc/caffeine} existing-cache)))
  (handle-request request))
```

## Two Common Patterns

### Pattern 1: No Caching by Default

Define functions with tags but no cache type. They won't cache outside scopes:

```clojure
(require '[memento.core :as m]
         '[memento.config :as mc])

;; Tags only, no mc/type = no caching by default
(m/defmemo get-user
  {mc/tags [:request]}
  [user-id]
  (db/fetch-user user-id))

;; Outside any scope: no caching (safe default)
;; Inside with-caches: uses the provided cache

(m/with-caches :request
  (constantly (m/create {mc/type mc/caffeine}))
  (handle-request request))
```

### Pattern 2: Swap Caches Within Scope

Define functions with a long-term cache, then swap to a fresh cache within requests:

```clojure
;; Function has a long-term cache by default
(m/defmemo get-user
  {mc/type mc/caffeine
   mc/ttl [1 :h]
   mc/tags [:request]}
  [user-id]
  (db/fetch-user user-id))

;; Outside scope: uses the long-term cache (1 hour TTL)
;; Inside scope: uses a fresh request-scoped cache

(m/with-caches :request
  (constantly (m/create {mc/type mc/caffeine}))
  (handle-request request))
```

Both patterns work. Choose based on whether you want caching outside of scopes.

Inside `with-caches`:
- All tagged functions use the cache returned by the factory function
- The cache is discarded when the block exits
- Functions called with the same arguments within the block hit the cache

## Consulting Long-Term Caches

Often you want request-scoped caching but also want to benefit from a long-term cache. Use `m/consulting`:

```clojure
;; Long-term cache for users (1 hour TTL)
(def user-cache (m/create {mc/type mc/caffeine mc/ttl [1 :h] mc/size< 10000}))

;; Define with tags only - no mc/type
(m/defmemo get-user
  {mc/tags [:request]}
  [user-id]
  (db/fetch-user user-id))

;; Bind to the long-term cache by default (used outside request scope)
(m/bind #'get-user {} user-cache)

;; Middleware that creates a request cache consulting the long-term cache
(defn wrap-request-cache [handler]
  (fn [request]
    (m/with-caches :request
      (fn [existing-cache]
        ;; Create a request cache that CONSULTS the long-term cache
        (m/create (m/consulting {mc/type mc/caffeine} existing-cache)))
      (handler request))))
```

With `consulting`:
- Cache **hits** check request cache first, then long-term cache
- Cache **misses** fetch data and store in request cache only
- Long-term cache is **not modified** by request-scoped operations

This is the recommended pattern for most web applications.

## Nested Scopes

Scopes can be nested. The innermost `with-caches` for a given tag wins. This is useful for:

- **Batch processing**: Outer scope for the whole job, inner scope for each item
- **Testing**: Test fixture scope wrapping individual test scopes  
- **Multi-tenant**: Tenant scope containing request scopes

```clojure
(m/defmemo get-user
  {mc/tags [:batch :request]}  ; Participates in both scopes
  [user-id]
  (db/fetch-user user-id))

;; Outer scope for batch job (cache shared across all items)
(m/with-caches :batch
  (constantly (m/create {mc/type mc/caffeine mc/size< 10000}))
  
  (doseq [item items]
    ;; Inner scope for each item (fresh cache per item)
    (m/with-caches :request
      (constantly (m/create {mc/type mc/caffeine}))
      (process-item item))))
```

Functions can have multiple tags, letting them participate in different scoping strategies depending on which scope is active.

## Permanently Updating Caches

If you need to permanently change the cache for all tagged functions (not just within a scope), use `update-tag-caches!`:

```clojure
;; Replace ALL :request-tagged caches with new empty caches
(m/update-tag-caches! :request (constantly (m/create {mc/type mc/caffeine})))
```

This is useful for:
- Clearing all caches of a certain type
- Replacing caches during application reconfiguration
- Testing

## Inspecting Tagged Functions

### List Functions by Tag

```clojure
;; Get all mount points tagged with :request
(m/mounts-by-tag :request)
;; => #{#memento.mount.TaggedMountPoint{...} ...}

;; Get the caches being used
(m/caches-by-tag :request)
;; => [#memento.caffeine.CaffeineCache{...} ...]
```

### Get Tags for a Function

```clojure
(m/tags get-user)
;; => [:request :user]
```

## How `with-caches` Works

1. When entering the block, for each mount point (cached function) with the specified tag:
   - Your factory function is called with the mount point's current cache
   - The returned cache becomes the active cache for that function within the scope
2. A thread-local binding maps the tag to these new caches
3. Tagged functions check this binding and use the scoped cache
4. When the block exits, the binding is removed and caches can be GCed

This means:
- Scoped caches are thread-local (safe for concurrent requests)
- Nested `with-caches` blocks work correctly
- No manual cleanup needed
- Using `constantly` makes all tagged functions share one cache
- Using a function that creates new caches gives each function its own cache

## Comparison of Multi-Cache Types

When combining request-scoped and long-term caches, you have three options:

| Type | Use Case | After Miss |
|------|----------|------------|
| `m/consulting` | Request cache in front of long-term | Entry in request cache only |
| `m/tiered` | Local cache in front of external (Redis) | Entry in both caches |
| `m/daisy` | Pre-loaded cache with fallback | Entry in upstream only |

### `consulting` (Most Common for Request Scoping)

```clojure
(m/consulting {} long-term-cache)
```

- Check request cache, then long-term cache
- Only request cache is updated
- Long-term cache is read-only from request's perspective

### `tiered`

```clojure
(m/tiered {} long-term-cache)
```

- Check local, then upstream
- Both caches get the entry
- Use for local cache in front of slow external cache (Redis, etc.)

### `daisy`

```clojure
(m/daisy pre-loaded-cache upstream-cache)
```

- Return from local if present, else hit upstream
- Local is never updated
- Use when local has pre-loaded/fixed data

## Complete Example

```clojure
(ns myapp.cache
  (:require [memento.core :as m]
            [memento.config :as mc]))

;; Long-term caches (used outside request scope, or consulted within)
(def user-cache (m/create {mc/type mc/caffeine mc/ttl [1 :h] mc/size< 10000}))
(def product-cache (m/create {mc/type mc/caffeine mc/ttl [5 :m] mc/size< 50000}))

;; Cached functions - tags only, no mc/type
(m/defmemo get-user
  {mc/tags [:request :user]}
  [user-id]
  (db/fetch-user user-id))
(m/bind #'get-user {} user-cache)  ; Bind to long-term cache

(m/defmemo get-product
  {mc/tags [:request]}
  [product-id]
  (db/fetch-product product-id))
(m/bind #'get-product {} product-cache)

;; Middleware - provides request-scoped cache that consults long-term
(defn wrap-request-cache [handler]
  (fn [request]
    (m/with-caches :request
      (fn [existing]
        (m/create (m/consulting {mc/type mc/caffeine} existing)))
      (handler request))))

;; Handler
(defn handle-product-page [request]
  (let [user (get-user (:user-id request))          ; Checks request cache, 
        product (get-product (:product-id request)) ; then consults long-term
        user-again (get-user (:user-id request))]   ; Hits request cache
    (render-page user product)))
```

In this example:
- Functions are defined with tags but no `mc/type`
- They're bound to long-term caches (used outside request scope)
- Within a request, `with-caches` provides a fresh cache that consults the long-term one
- Repeated calls within a request hit the request cache
- Long-term caches provide data but aren't modified by request operations
- Request caches are automatically discarded after each request
