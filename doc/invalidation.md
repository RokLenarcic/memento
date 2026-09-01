# Invalidation Guide

This guide covers all the ways to clear and invalidate cache entries in Memento.

## Why Invalidation Matters

Cached data becomes stale when the underlying data changes. Good invalidation ensures users see fresh data while still benefiting from caching. Memento provides several invalidation strategies:

1. **Manual clearing** - Clear specific entries or entire caches
2. **Secondary-index invalidation** - Clear related entries across multiple functions with one call
3. **Automatic expiration** - TTL and fade (covered in [Configuration](configuration.md))

## Manual Cache Clearing

### Clear All Entries for a Function

```clojure
(require '[memento.core :as m])

(m/memo-clear! get-user)  ; Clears all cached results for get-user
```

### Clear a Specific Entry

Pass the same arguments that were used to cache the value:

```clojure
;; Clear the cached result for (get-user 123)
(m/memo-clear! get-user 123)

;; For multi-argument functions
(m/memo-clear! get-user-orders 123 :pending)  ; Clears (get-user-orders 123 :pending)
```

### Clear an Entire Cache

If multiple functions share a cache, clear all entries at once:

```clojure
(def shared-cache (m/create {mc/type mc/caffeine mc/size< 10000}))
(m/bind #'get-user {} shared-cache)
(m/bind #'get-user-orders {} shared-cache)

;; Clears entries from BOTH functions
(m/memo-clear-cache! shared-cache)
```

## Secondary-Index Invalidation

The real power of Memento is invalidating related data across multiple functions with a single call. A secondary index maps arbitrary secondary IDs to cache entries. Secondary IDs are independent of mount tags: mount tags only select functions for scoped caching and event broadcast.

### The N×M Problem

In a typical application you have:
- **N cached functions** that read entity data (e.g., `get-user`, `get-user-orders`, `get-user-preferences`)
- **M functions** that modify entity data (e.g., `update-user!`, `delete-user!`, `merge-users!`)

Without secondary-index invalidation, every modifying function must know about every cached function:

```clojure
;; Every modifier must list ALL cached functions - maintenance nightmare!
(defn update-user! [user-id data]
  (db/update-user! user-id data)
  (m/memo-clear! get-user user-id)
  (m/memo-clear! get-user-orders user-id)
  (m/memo-clear! get-user-preferences user-id)
  ;; Did we forget one? Will we remember to add new ones?
  )
```

This creates an N×M maintenance burden:
- Adding a new cached function means updating all M modifiers
- Adding a new modifier means knowing all N cached functions
- It's easy to forget one and have stale data bugs

### The Solution: Secondary IDs

Secondary-index invalidation decouples producers from consumers. They only need to agree on a secondary-ID scheme. A vector such as `[:user user-id]` is a useful composite secondary ID, but any hashable value works:

```clojure
;; CACHED FUNCTIONS: index returned entries, don't care who invalidates
(m/defmemo get-user
  {mc/type mc/caffeine}
  [user-id]
  (-> (db/fetch-user user-id)
      (m/with-sec-id [:user user-id])))

;; MODIFYING FUNCTIONS: invalidate the matching secondary ID.
(defn update-user! [user-id data]
  (m/with-invalidation [[:user user-id]]
    (db/update-user! user-id data)))
```

Now you can add cached functions or modifiers independently.

### Step-by-Step Setup

#### Step 1: Define Cached Functions

```clojure
(m/defmemo get-user
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-user user-id))

(m/defmemo get-user-orders
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-orders user-id))
```

#### Step 2: Add Secondary IDs to Return Values

Use `m/with-sec-id` to associate cached values with entity IDs:

```clojure
(m/defmemo get-user
  {mc/type mc/caffeine}
  [user-id]
  (-> (db/fetch-user user-id)
      (m/with-sec-id [:user user-id])))

(m/defmemo get-user-orders
  {mc/type mc/caffeine}
  [user-id]
  (-> (db/fetch-orders user-id)
      (m/with-sec-id [:user user-id])))
```

#### Step 3: Invalidate by Secondary ID

```clojure
;; Clears every entry indexed by [:user 123].
(m/memo-clear-sec-id! [:user 123])
```

For writes, prefer `with-invalidation`: it starts the invalidation before the write so loads that overlap the write cannot publish stale results.

### Multiple Secondary IDs per Entry

A cached value can have multiple secondary IDs. This is essential for aggregated data like dashboards.

#### Simple case: Two related entities

```clojure
(m/defmemo get-order
  {mc/type mc/caffeine}
  [order-id]
  (let [order (db/fetch-order order-id)]
    (-> order
        (m/with-sec-id [:order order-id])
        (m/with-sec-id [:user (:user-id order)]))))

;; Now you can invalidate by either:
(m/memo-clear-sec-id! [:order 456])  ; Clear this specific order
(m/memo-clear-sec-id! [:user 123])   ; Clear all orders for user 123
```

#### Complex case: Aggregated data

Consider a dashboard showing the last 10 users who logged in. If any of those users is modified, the dashboard cache should be invalidated:

```clojure
(m/defmemo get-recent-users-dashboard
  {mc/type mc/caffeine}
  []
  (let [users (db/fetch-recent-users 10)]
    ;; Add an ID for every user that appears in this cached result.
    (reduce (fn [result user]
              (m/with-sec-id result [:user (:id user)]))
            {:users users :generated-at (java.time.Instant/now)}
            users)))

;; Now if ANY of those 10 users is modified:
(defn update-user! [user-id data]
  (m/with-invalidation [[:user user-id]]
    (db/update-user! user-id data)))
```

The dashboard is automatically invalidated when any user it displays is modified, but NOT when unrelated users are modified.

### Using `ret-fn` for Cleaner Code

Instead of adding `with-sec-id` inside your function, use `ret-fn` to separate caching concerns:

```clojure
(defn index-user-data [[user-id] result]
  (m/with-sec-id result [:user user-id]))

(m/defmemo get-user
  {mc/type mc/caffeine
   mc/ret-fn index-user-data}
  [user-id]
  (db/fetch-user user-id))  ; Clean function, no caching logic
```

### Invalidation Around a Write

Start the invalidation before changing underlying data when a concurrent cache load could otherwise
read stale data during the write. The returned function is single-use: call it with `true` after a
successful write to clear matching entries, or `false` after a failed write to only end the lockout.

```clojure
(let [finish! (m/start-invalidation! [:user user-id])]
  (try
    (db/update-user! user-id data)
    (finish! true)
    (catch Throwable t
      (finish! false)
      (throw t))))
```

`with-invalidation` provides the same lifecycle and ends the lockout without clearing if its body
throws:

```clojure
(m/with-invalidation [[:user user-id]]
  (db/update-user! user-id data))
```

## Manually Adding Cache Entries

You can pre-populate or manually update cache entries:

```clojure
;; Add entries to a function's cache
;; Keys are argument vectors, values are the cached results
(m/memo-add! get-user {[123] {:id 123 :name "Alice"}
                       [456] {:id 456 :name "Bob"}})
```

This is useful for:
- Pre-warming caches on startup
- Updating cache after a write operation (instead of invalidating)
- Populating related caches from a bulk fetch (see [Events](advanced.md#events))

## Conditional Caching

### Prevent Caching Specific Values

Use `m/do-not-cache` to prevent certain results from being cached:

```clojure
(m/defmemo get-user
  {mc/type mc/caffeine}
  [user-id]
  (if-let [user (db/fetch-user user-id)]
    user
    (m/do-not-cache nil)))  ; Don't cache "not found" results
```

Or use `ret-fn` for cleaner separation:

```clojure
(defn no-cache-errors [_ response]
  (if (>= (:status response) 400)
    (m/do-not-cache response)
    response))

(m/defmemo fetch-api-data
  {mc/type mc/caffeine
   mc/ret-fn no-cache-errors}
  [endpoint]
  (http/get endpoint))
```

### Check if Value is Cached

Use `if-cached` to check without triggering a cache miss:

```clojure
(m/if-cached [user (get-user 123)]
  (println "User was cached:" user)
  (println "User not in cache"))
```

## Invalidation Patterns

### Write-Through Pattern

Update cache after successful writes:

```clojure
(defn update-user! [user-id data]
  (let [updated-user (db/update-user! user-id data)]
    ;; Option 1: Invalidate and let next read refresh
    (m/memo-clear-sec-id! [:user user-id])
    
    ;; Option 2: Update cache directly (write-through)
    (m/memo-add! get-user {[user-id] updated-user})
    
    updated-user))
```

### Event-Driven Invalidation

Invalidate based on events from a message queue:

```clojure
(defn handle-event [event]
  (case (:type event)
    :user-updated (m/memo-clear-sec-id! [:user (:user-id event)])
    :order-completed ((m/start-invalidation! [:order (:order-id event)]
                                              [:user (:user-id event)]) true)
    nil))
```

### Invalidate All Functions with a Tag

```clojure
;; Get all mount points for a tag
(m/mounts-by-tag :user)

;; Clear all caches for a tag (without specifying an ID)
(doseq [mp (m/mounts-by-tag :user)]
  (m/memo-clear! mp))
```

## Concurrency Considerations

### Single Load Per Key

If multiple threads request the same uncached key simultaneously, only one actually calls the function. The others wait and receive the same result.

### Invalidation During Load

If a key is invalidated while being loaded, the load is retried to ensure fresh data. The loading thread is interrupted when an invalidation finds its `SpecialPromise` through an existing index entry; first loads with result-derived secondary IDs are instead detected by timeline validation when they complete. There is a narrow boundary after a completed load is published: an invalidation may remove the published cache entry while the loader or callers already waiting on that load still return its computed value. Subsequent calls miss and load fresh data.

### The Call Tree Problem

The hardest concurrency challenge with caching is **invalidating call trees** - cached functions that call other cached functions.

Consider this scenario:

```clojure
(m/defmemo get-user-summary [user-id]    ; Calls get-user-details
  (let [details (get-user-details user-id)]
    (summarize details)))

(m/defmemo get-user-details [user-id]    ; Lower-level cache
  (db/fetch-user user-id))
```

When you invalidate both caches, there's a race condition:

1. You invalidate `get-user-summary` for user 123
2. Before you invalidate `get-user-details`, another thread calls `get-user-summary`
3. That call misses, calls `get-user-details`, which still has **stale data**
4. The stale data gets cached in the freshly-cleared `get-user-summary`
5. You finally invalidate `get-user-details` - but `get-user-summary` now has stale data

### Solutions

#### Secondary-Index Invalidation with Lockout

When using secondary-index invalidation (`memo-clear-sec-id!`, `start-invalidation!`, or
`with-invalidation`), Memento records serialized start and end transitions on a timeline while each
loaded cache backend clears its own indexes. Timeline reads remain lock-free, and loads validate the
timeline before publication to coordinate concurrent loads without relying on mount tags.

```clojure
;; Secondary-ID invalidation coordinates across functions.
(m/memo-clear-sec-id! [:user user-id])
```

#### Request-Scoped Caching with Liberal Clearing

Request-scoped caching sidesteps the problem entirely: each request starts with a fresh cache and discards it at the end. Within a request, you can be very liberal with cache clearing - just nuke everything after any write operation:

```clojure
(defn handle-request [request]
  (m/with-caches :request (constantly (m/create {mc/type mc/caffeine}))
    ;; ... do reads, cache is populated ...
    
    (when (write-operation? request)
      ;; After any DB write, just clear the entire request cache
      ;; No need to be precise - it's cheap and guarantees correctness
      (m/memo-clear-cache! (m/active-cache get-user))
      ;; Or clear all caches for the tag:
      (doseq [mp (m/mounts-by-tag :request)]
        (m/memo-clear! mp)))
    
    ;; ... continue with fresh data ...
    ))
```

You sacrifice some caching performance (entries you could have kept are cleared), but you gain simplicity and correctness. Since the cache only lives for one request anyway, the cost is limited. This is much easier than tracking exactly which cached functions are affected by each write.

#### Shared Cache with Full Clear

For long-lived caches, if you can afford to clear *all* cached data (not just one entity), backing related functions with a shared cache and clearing it atomically eliminates the race condition:

```clojure
(def user-cache (m/create {mc/type mc/caffeine mc/size< 10000}))

(m/bind #'get-user-details {} user-cache)
(m/bind #'get-user-summary {} user-cache)

;; Clears ALL entries for ALL functions - atomic, no race condition
(m/memo-clear-cache! user-cache)
```

This is a sledgehammer approach - it clears everything, not just user 123's data. Only practical when you genuinely need to invalidate all cached data.

Most caching libraries don't address the call tree problem at all. Memento's secondary-index invalidation with lockout coordination handles the common cases correctly.

See [Internals](internals.md) for details on the lockout mechanism.
