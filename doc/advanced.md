# Advanced Features

This guide covers Memento's advanced features for complex caching scenarios.

## Tiered Caching

Tiered caching combines multiple caches, typically a fast local cache in front of a slower but larger upstream cache.

### Use Cases

- **Local + External**: Fast in-memory cache in front of Redis/Memcached
- **Request + Long-term**: Request-scoped cache consulting application cache
- **L1 + L2**: Small fast cache in front of larger slower cache

### Types of Tiered Caches

Memento provides three combining strategies:

#### `m/tiered` - Both Updated

```clojure
(m/tiered local-cache upstream-cache)
```

- Check local first, then upstream
- After a miss: entry is stored in **both** caches
- Use when: Local is fast, upstream is slow but shared

```clojure
(def local-cache (m/create {mc/type mc/caffeine mc/size< 100 mc/ttl [1 :m]}))
(def redis-cache (create-redis-cache))  ; Hypothetical Redis implementation

(m/defmemo get-user 
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-user user-id))

(m/bind #'get-user {} (m/tiered local-cache redis-cache))
```

#### `m/consulting` - Local Updated Only

```clojure
(m/consulting local-cache upstream-cache)
```

- Check local first, then upstream
- After a miss: entry stored in **local only**
- Upstream is read-only from local's perspective

Best for request-scoped caching (see [Scoped Caching Guide](scoped-caching.md)).

#### `m/daisy` - Upstream Updated Only

```clojure
(m/daisy local-cache upstream-cache)
```

- Return from local if present, else hit upstream
- Local cache is **never updated** by cache operations
- Use when: Local has pre-loaded/fixed data

```clojure
;; Pre-loaded cache with default values
(def defaults-cache 
  (m/create {mc/type mc/caffeine
             mc/seed {[:theme] "light"
                      [:language] "en"}}))

(def user-prefs-cache (m/create {mc/type mc/caffeine mc/ttl [1 :h]}))

(m/defmemo get-preference 
  {mc/type mc/caffeine}
  [key]
  (db/fetch-preference key))

;; Check defaults first, fall back to user preferences
(m/bind #'get-preference {} (m/daisy defaults-cache user-prefs-cache))
```

### Invalidation in Tiered Caches

Invalidation operations affect **both** caches in tiered setups:

```clojure
(m/memo-clear! get-user 123)  ; Clears from both local AND upstream
```

Other operations (like `as-map`) only affect the local cache.

## Events (N+1 Query Prevention)

Events solve the **N+1 query problem** for single-item cached functions. When you load a list of N items, you can populate N cache entries - avoiding N future database queries.

### The Problem: N+1 Queries

You have a function that fetches one user's email by ID. It's cached, so repeated calls are fast. But what happens when you load a list of 100 users elsewhere?

```clojure
;; Bulk load - returns [{:id 1 :email "alice@example.com"} {:id 2 :email "bob@example.com"} ...]
(defn get-all-users []
  (db/fetch-all-users))

;; Individual lookups (cached)
(m/defmemo get-user-email
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-user-email user-id))
```

After calling `get-all-users`, you already have every user's email in memory. But `get-user-email` doesn't know that. When you later call `(get-user-email 1)`, `(get-user-email 2)`, etc., each one hits the database - **100 extra queries for data you already had**.

### The Solution: Fire Events to Warm Caches

Use `mc/evt-fn` to define how a function should handle incoming events, then `fire-event!` to broadcast data:

```clojure
(require '[memento.core :as m]
         '[memento.config :as mc])

;; Individual lookup with event handler
(m/defmemo get-user-email
  {mc/type mc/caffeine
   mc/tags [:user]
   mc/evt-fn (m/evt-cache-add 
               :user-seen
               (fn [{:keys [id email]}] 
                 {[id] email}))}  ; Maps event payload to cache entry
  [user-id]
  (db/fetch-user-email user-id))

;; Bulk load fires events to warm the cache
(defn get-all-users []
  (let [users (db/fetch-all-users)]
    ;; Fire event for each user to all :user-tagged functions
    (doseq [u users]
      (m/fire-event! :user [:user-seen u]))
    users))
```

Now when `get-all-users` loads 100 users, it fires 100 events. Each `:user`-tagged function receives those events and populates its cache. Subsequent calls to `(get-user-email 1)` return instantly from cache.

### Multiple Consumers from One Event

The real power comes when you have multiple cached functions for the same entity:

```clojure
;; All these functions are tagged :user and handle :user-seen events
(m/defmemo get-user-email
  {mc/type mc/caffeine
   mc/tags [:user]
   mc/evt-fn (m/evt-cache-add :user-seen
               (fn [{:keys [id email]}] {[id] email}))}
  [user-id]
  (db/fetch-user-email user-id))

(m/defmemo get-user-name
  {mc/type mc/caffeine
   mc/tags [:user]
   mc/evt-fn (m/evt-cache-add :user-seen
               (fn [{:keys [id name]}] {[id] name}))}
  [user-id]
  (db/fetch-user-name user-id))

(m/defmemo get-user-role
  {mc/type mc/caffeine
   mc/tags [:user]
   mc/evt-fn (m/evt-cache-add :user-seen
               (fn [{:keys [id role]}] {[id] role}))}
  [user-id]
  (db/fetch-user-role user-id))
```

One `fire-event!` call broadcasts to **all** `:user`-tagged functions. Each extracts what it needs from the payload.

### `evt-cache-add` Helper

`evt-cache-add` creates an event handler that:
1. Matches events of a specific type (first element of event vector)
2. Transforms the event payload into cache entries
3. Adds those entries to the function's cache

```clojure
(m/evt-cache-add 
  :event-type           ; Only handle [:event-type payload] events
  (fn [payload]         ; Transform payload to cache entries
    {[arg1 arg2] value  ; Map of [args] -> cached-value
     [arg3] value2}))   ; Can return multiple entries
```

### Firing Events

```clojure
;; Fire to all functions tagged with :user
(m/fire-event! :user [:user-seen {:id 1 :email "alice@example.com" :name "Alice"}])

;; Fire to a specific function only
(m/fire-event! get-user-email [:user-seen {:id 1 :email "alice@example.com"}])
```

### Custom Event Handlers

For complex scenarios (invalidation, logging, conditional caching), write your own handler:

```clojure
(defn my-event-handler [mount-point event]
  (let [[event-type payload] event]
    (case event-type
      :user-seen 
      (m/memo-add! mount-point {[(:id payload)] (:email payload)})
      
      :user-deleted
      (m/memo-clear! mount-point (:id payload))
      
      nil)))  ; Unknown events ignored

(m/defmemo get-user-email
  {mc/type mc/caffeine
   mc/evt-fn my-event-handler}
  [user-id]
  (db/fetch-user-email user-id))
```

### Events + Tags: The Complete Pattern

Tags serve three purposes in Memento:
1. **Scoped caching** - `with-caches` enables caching for tagged functions
2. **Bulk invalidation** - `memo-clear-tag!` clears entries by tag + ID
3. **Cache warming** - `fire-event!` broadcasts data to tagged functions

Together they let you build efficient caching without tight coupling between functions.

## Variable Expiry

Instead of fixed TTL/fade for all entries, set expiry per-entry based on the value.

### Using the `Expiry` Interface

Implement `memento.caffeine.Expiry`:

```clojure
(import '[memento.caffeine Expiry])

;; Cache downstream service responses:
;; - Success (2xx): cache for 1 hour
;; - Server errors (5xx): cache briefly to avoid hammering a failing service
(def service-response-expiry
  (reify Expiry
    (ttl [this segment key response]
      (if (>= (:status response) 500)
        [5 :m]     ; Errors: cache 5 minutes, then retry
        [1 :h]))   ; Success: cache 1 hour
    (fade [this segment key value]
      nil)))

(m/defmemo call-downstream-service
  {mcc/expiry service-response-expiry}
  [endpoint]
  (http/get endpoint))
```

The `Expiry` interface has two methods:
- `ttl [this segment key value]` - Return TTL duration or nil
- `fade [this segment key value]` - Return fade duration or nil

Return `nil` to use the cache's base `ttl` or `fade` setting.

### Using Metadata

A built-in implementation reads expiry from value metadata:

```clojure
(require '[memento.caffeine.config :as mcc])

;; OAuth tokens - cache until they expire
(m/defmemo get-access-token
  {mcc/expiry mcc/meta-expiry}
  [client-id]
  (let [token (oauth/fetch-token client-id)
        expires-in (:expires_in token)]  ; seconds until expiry
    (with-meta token
      {mc/ttl [(- expires-in 60) :s]}))) ; refresh 1 minute early
```

Set `mc/ttl` and/or `mc/fade` in metadata to control expiry.

## Namespace Scanning

Automatically attach caches to annotated functions across namespaces.

### Annotate Functions

```clojure
(ns myapp.users
  (:require [memento.core :as m]
            [memento.config :as mc]))

;; Add ::m/cache to function metadata
(defn ^{::m/cache {mc/ttl [5 :m]}} get-user
  [user-id]
  (db/fetch-user user-id))

;; Or use defn's metadata syntax
(defn get-user-orders
  {::m/cache {mc/ttl [10 :m] mc/tags [:user]}}
  [user-id]
  (db/fetch-orders user-id))
```

### Scan and Attach

```clojure
(require '[memento.ns-scan :as ns-scan])

;; Scan all loaded namespaces and attach caches
(ns-scan/attach-caches)
```

This finds all vars with `::m/cache` metadata and calls `m/memo` on them.

### Options

```clojure
;; Custom namespace filter (default excludes clojure.* and nrepl.*)
(ns-scan/attach-caches {:blacklist #"^(clojure|nrepl|myapp\.internal)\..*"})
```

### When to Use

- Application startup to initialize all caches
- After namespace reloading in development
- When you prefer declarative cache configuration

**Note**: Only works on already-loaded namespaces. Ensure namespaces are required before calling `attach-caches`.

## Shared Caches Across Functions

One cache can back multiple functions, sharing size limits:

```clojure
;; Single cache with 10,000 entry limit
(def user-data-cache (m/create {mc/size< 10000}))

;; Both functions share the cache
(m/bind #'get-user {} user-data-cache)
(m/bind #'get-user-preferences {} user-data-cache)

;; Together they can have at most 10,000 entries
;; Entries are evicted based on overall LRU, not per-function
```

This is useful when:
- You want a global memory limit across related functions
- Functions access similar data and benefit from shared entries
- You want simpler cache management

## `if-cached` Conditional

Check if a value is cached without triggering a miss:

```clojure
(m/if-cached [user (get-user 123)]
  ;; Value was cached
  (println "Got cached user:" (:name user))
  ;; Value was not cached (function NOT called)
  (println "User not in cache"))
```

Use cases:
- Optimistic UI updates (show cached data immediately)
- Conditional expensive operations
- Cache warming checks

## Weight-Based Eviction

Instead of counting entries, evict based on total weight:

```clojure
(require '[memento.caffeine.config :as mcc])

(m/defmemo get-document
  {mcc/weight< 100000000  ; 100MB total
   mcc/kv-weight (fn [id key value]
                   (count (:content value)))}  ; Weight = content size
  [doc-id]
  (db/fetch-document doc-id))
```

Useful when cached values have highly variable sizes.

## Weak/Soft References

Allow GC to reclaim cached values under memory pressure:

```clojure
(require '[memento.caffeine.config :as mcc])

;; Weak values - GC can reclaim anytime
(m/defmemo get-large-object
  {mcc/weak-values true}
  [id]
  (load-large-object id))

;; Soft values - GC reclaims only under memory pressure
(m/defmemo get-medium-object
  {mcc/soft-values true}
  [id]
  (load-medium-object id))
```

Use for:
- Large objects that can be recomputed
- Caches that shouldn't cause OutOfMemoryError
- Memory-sensitive applications

## Removal Listener

Get notified when entries are evicted:

```clojure
(require '[memento.caffeine.config :as mcc])

(m/defmemo get-resource
  {mc/size< 100
   mcc/removal-listener (fn [id key value cause]
                          (println "Evicted" key "because" cause)
                          (when (= cause :explicit)
                            (cleanup-resource value)))}
  [resource-id]
  (acquire-resource resource-id))
```

Causes: `:explicit`, `:replaced`, `:collected`, `:expired`, `:size`

## Cache Statistics

Enable and retrieve cache statistics:

```clojure
(require '[memento.caffeine.config :as mcc])

(m/defmemo get-user
  {mcc/stats true}
  [user-id]
  (db/fetch-user user-id))

;; After some usage...
(m/stats get-user)
;; => {:hit-count 1523
;;     :miss-count 234
;;     :eviction-count 12
;;     ...}
```
