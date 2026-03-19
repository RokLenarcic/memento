# Invalidation Guide

This guide covers all the ways to clear and invalidate cache entries in Memento.

## Why Invalidation Matters

Cached data becomes stale when the underlying data changes. Good invalidation ensures users see fresh data while still benefiting from caching. Memento provides several invalidation strategies:

1. **Manual clearing** - Clear specific entries or entire caches
2. **Tag-based invalidation** - Clear related entries across multiple functions with one call
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

## Tag-Based Invalidation

The real power of Memento is invalidating related data across multiple functions with a single call. This uses a **secondary index** that maps entity IDs to cache entries.

### The N×M Problem

In a typical application you have:
- **N cached functions** that read entity data (e.g., `get-user`, `get-user-orders`, `get-user-preferences`)
- **M functions** that modify entity data (e.g., `update-user!`, `delete-user!`, `merge-users!`)

Without tag-based invalidation, every modifying function must know about every cached function:

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

### The Solution: Tag IDs

Tag-based invalidation decouples producers from consumers. They only need to agree on a tag name:

```clojure
;; CACHED FUNCTIONS: tag with :user, don't care who invalidates
(m/defmemo get-user
  {mc/type mc/caffeine, mc/tags [:user]}
  [user-id]
  (-> (db/fetch-user user-id)
      (m/with-tag-id :user user-id)))

;; MODIFYING FUNCTIONS: invalidate :user tag, don't care who's cached  
(defn update-user! [user-id data]
  (db/update-user! user-id data)
  (m/memo-clear-tag! :user user-id))  ; Clears ALL :user-tagged caches
```

Now you can add cached functions or modifiers independently.

### Step-by-Step Setup

#### Step 1: Add Tags to Functions

```clojure
(m/defmemo get-user
  {mc/type mc/caffeine
   mc/tags [:user]}  ; This function participates in :user invalidation
  [user-id]
  (db/fetch-user user-id))

(m/defmemo get-user-orders
  {mc/type mc/caffeine
   mc/tags [:user]}
  [user-id]
  (db/fetch-orders user-id))
```

#### Step 2: Tag Return Values with Entity IDs

Use `m/with-tag-id` to associate cached values with entity IDs:

```clojure
(m/defmemo get-user
  {mc/type mc/caffeine
   mc/tags [:user]}
  [user-id]
  (-> (db/fetch-user user-id)
      (m/with-tag-id :user user-id)))  ; "This result is about user `user-id`"

(m/defmemo get-user-orders
  {mc/type mc/caffeine
   mc/tags [:user]}
  [user-id]
  (-> (db/fetch-orders user-id)
      (m/with-tag-id :user user-id)))
```

#### Step 3: Invalidate by Tag + ID

```clojure
;; Clears ALL entries tagged with [:user 123] from ALL :user-tagged functions
(m/memo-clear-tag! :user 123)
```

### Multiple Tags per Entry

A cached value can be tagged with multiple entity IDs. This is essential for aggregated data like dashboards.

#### Simple case: Two related entities

```clojure
(m/defmemo get-order
  {mc/type mc/caffeine
   mc/tags [:user :order]}
  [order-id]
  (let [order (db/fetch-order order-id)]
    (-> order
        (m/with-tag-id :order order-id)
        (m/with-tag-id :user (:user-id order)))))

;; Now you can invalidate by either:
(m/memo-clear-tag! :order 456)  ; Clear this specific order
(m/memo-clear-tag! :user 123)   ; Clear all orders for user 123
```

#### Complex case: Aggregated data

Consider a dashboard showing the last 10 users who logged in. If any of those users is modified, the dashboard cache should be invalidated:

```clojure
(m/defmemo get-recent-users-dashboard
  {mc/type mc/caffeine
   mc/tags [:user]}
  []
  (let [users (db/fetch-recent-users 10)]
    ;; Tag with ALL user IDs that appear in this cached result
    (reduce (fn [result user]
              (m/with-tag-id result :user (:id user)))
            {:users users :generated-at (java.time.Instant/now)}
            users)))

;; Now if ANY of those 10 users is modified:
(defn update-user! [user-id data]
  (db/update-user! user-id data)
  (m/memo-clear-tag! :user user-id))  ; Clears dashboard if this user was in it
```

The dashboard is automatically invalidated when any user it displays is modified, but NOT when unrelated users are modified.

### Using `ret-fn` for Cleaner Code

Instead of adding `with-tag-id` inside your function, use `ret-fn` to separate caching concerns:

```clojure
(defn tag-user-data [[user-id] result]
  (m/with-tag-id result :user user-id))

(m/defmemo get-user
  {mc/type mc/caffeine
   mc/tags [:user]
   mc/ret-fn tag-user-data}
  [user-id]
  (db/fetch-user user-id))  ; Clean function, no caching logic
```

### Bulk Invalidation

For better atomicity when invalidating multiple entities:

```clojure
;; Invalidate multiple tag+id pairs atomically
(m/memo-clear-tags! [:user 123] [:user 456] [:order 789])
```

This ensures all invalidations happen together, preventing race conditions where some data is cleared but related data isn't.

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
    (m/memo-clear-tag! :user user-id)
    
    ;; Option 2: Update cache directly (write-through)
    (m/memo-add! get-user {[user-id] updated-user})
    
    updated-user))
```

### Event-Driven Invalidation

Invalidate based on events from a message queue:

```clojure
(defn handle-event [event]
  (case (:type event)
    :user-updated (m/memo-clear-tag! :user (:user-id event))
    :order-completed (m/memo-clear-tags! 
                       [:order (:order-id event)]
                       [:user (:user-id event)])
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

If a key is invalidated while being loaded, the load is retried to ensure fresh data. The loading thread is interrupted when a tag is invalidated.

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

#### Tag-Based Invalidation with Lockout

When using tag-based invalidation (`memo-clear-tag!`), Memento uses locking mechanisms to coordinate invalidation across all tagged functions. This is a best-effort solution - it significantly reduces the window for race conditions but cannot eliminate them entirely in all edge cases.

```clojure
;; Tag-based invalidation coordinates across functions
(m/memo-clear-tag! :user user-id)
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

Most caching libraries don't address the call tree problem at all. Memento's tag-based invalidation with lockout coordination handles the common cases correctly.

See [Internals](internals.md) for details on the lockout mechanism.
