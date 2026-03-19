# Configuration Guide

This guide covers all configuration options for Memento caches.

## Overview

Memento has two types of configuration:

1. **Cache configuration** - Settings for the cache itself (size, TTL, etc.)
2. **Mount configuration** - Settings for how a function connects to a cache (key transformation, tags, etc.)

When using `defmemo` or the 2-argument `memo`, you can mix both in a single map - Memento separates them automatically.

## Basic Usage

### `defmemo` Macro

`defmemo` works just like `defn` - the configuration map is standard Clojure function metadata. You can include any metadata you'd normally use with `defn`, plus the Memento cache settings:

```clojure
(require '[memento.core :as m]
         '[memento.config :as mc])

;; Always-on caching - specify mc/type
(m/defmemo get-user
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-user user-id))

;; With additional settings
(m/defmemo get-user
  {mc/type mc/caffeine
   mc/ttl [5 :m]
   mc/size< 1000}
  [user-id]
  (db/fetch-user user-id))

;; For scoped caching - tags only, no mc/type (no caching until scope entered)
(m/defmemo get-user
  {mc/tags [:request]}
  [user-id]
  (db/fetch-user user-id))

;; With docstring and mixed metadata (cache settings + standard metadata)
(m/defmemo get-user
  "Fetches a user by ID."
  {mc/type mc/caffeine
   mc/ttl [5 :m]
   :private true
   :added "1.0"}
  [user-id]
  (db/fetch-user user-id))
```

### `memo` Function

Wrap an existing function or var:

```clojure
;; Wrap a function
(def cached-fn (m/memo (fn [x] (* x x)) {mc/type mc/caffeine mc/size< 100}))

;; Wrap a var (modifies the var's root binding)
(defn get-user [user-id] (db/fetch-user user-id))
(m/memo #'get-user {mc/type mc/caffeine mc/ttl [5 :m]})
```

### Separate Cache and Mount Configuration

For advanced control, use 3-argument `memo` or explicit `create` + `bind`:

```clojure
;; 3-argument memo: fn, mount-conf, cache-conf
(m/memo #'get-user 
        {mc/tags [:user]}                            ; mount config
        {mc/type mc/caffeine mc/ttl [5 :m] mc/size< 100}) ; cache config

;; Or explicitly
(def my-cache (m/create {mc/type mc/caffeine mc/ttl [5 :m] mc/size< 100}))
(m/bind #'get-user {mc/tags [:user]} my-cache)
```

## Cache Configuration Options

These settings control the cache itself. Use vars from `memento.config` (aliased as `mc`).

| Option | Description | Example |
|--------|-------------|---------|
| `mc/type` | Cache implementation: `mc/caffeine` or `mc/none` | `{mc/type mc/caffeine}` |
| `mc/size<` | Max entries (LRU eviction) | `{mc/size< 1000}` |
| `mc/ttl` | Time-to-live since creation | `{mc/ttl [5 :m]}` |
| `mc/fade` | Expiry since last access | `{mc/fade [10 :m]}` |
| `mc/initial-capacity` | Initial hash table size hint | `{mc/initial-capacity 256}` |

**Time units:** `:ns`, `:us`, `:ms`, `:s`, `:m`, `:h`, `:d` — or just a number for seconds.

**Note:** If you omit `mc/type`, the function uses a no-op cache (no caching). This is intentional for scoped caching patterns where caching only happens inside `with-caches`. See the [Scoped Caching Guide](scoped-caching.md).

## Mount Configuration Options

These settings control how a function connects to its cache.

### `mc/tags`

Tags for grouping functions. Used for scoped caching and bulk invalidation.

```clojure
{mc/tags [:user]}                    ; Single tag
{mc/tags [:user :request]}           ; Multiple tags

;; Shorthand syntax (tags only, no other config)
(m/memo #'get-user :user)            ; Same as {mc/tags [:user]}
(m/memo #'get-user [:user :request]) ; Same as {mc/tags [:user :request]}
```

### `mc/id`

Identifier for the function's cache entries. This determines which entries "belong" to which function when multiple functions share a cache.

**Default behavior:**
- For vars (`#'get-user`): the var name becomes the ID — entries survive namespace reloads
- For inline/anonymous functions: the function object itself is the ID — each new function instance gets its own entries

**When to set explicitly:**

Setting an explicit ID allows entries to be shared across function instances and survive reloads:

```clojure
;; Without mc/id: each call creates a new function with its own empty cache
(defn make-fetcher []
  (m/memo (fn [id] (db/fetch id)) {mc/type mc/caffeine}))

;; With mc/id: all instances share the same cache entries, survives reloads
(defn make-fetcher []
  (m/memo (fn [id] (db/fetch id)) {mc/type mc/caffeine mc/id :fetcher}))
```

You can also incorporate closure data into the ID to create separate entry pools per some parameter:

```clojure
;; Each tenant gets its own cache entries
(defn make-tenant-fetcher [tenant-id]
  (m/memo (fn [id] (db/fetch tenant-id id)) 
          {mc/type mc/caffeine mc/id [:tenant-fetcher tenant-id]}))
```

This works for vars too — nothing prevents you from setting an explicit ID on a var-backed function.

### `mc/key-fn`

Transform the argument list into a cache key. Useful when some arguments shouldn't affect caching.

The function receives the arguments as a sequence:

```clojure
;; Ignore the first argument (e.g., db connection)
(m/defmemo get-user
  {mc/key-fn rest}  ; Cache key is [user-id], not [db-conn user-id]
  [db-conn user-id]
  (db/fetch-user db-conn user-id))

;; Extract a specific field from a request
(m/defmemo get-current-user
  {mc/key-fn #(-> % first :session :user-id)}
  [request]
  (db/fetch-user (-> request :session :user-id)))
```

### `mc/key-fn*`

Like `key-fn`, but receives arguments as separate parameters (like the original function):

```clojure
(m/defmemo get-user
  {mc/key-fn* (fn [db-conn user-id] user-id)}
  [db-conn user-id]
  (db/fetch-user db-conn user-id))
```

### `mc/ret-fn`

Transform the return value before caching. Receives `[args value]`.

Common uses:
- Prevent caching certain values with `m/do-not-cache`
- Add tag IDs for invalidation with `m/with-tag-id`

```clojure
;; Don't cache error responses
(defn no-cache-errors [[args] response]
  (if (>= (:status response) 400)
    (m/do-not-cache response)
    response))

(m/defmemo get-user
  {mc/ret-fn no-cache-errors}
  [user-id]
  (api/fetch-user user-id))
```

### `mc/ret-ex-fn`

Transform exceptions before they're cached/rethrown. Receives `[args throwable]`.

```clojure
;; Don't cache transient errors
(defn no-cache-transient [[args] ex]
  (if (transient-error? ex)
    (m/do-not-cache ex)
    ex))

(m/defmemo get-user
  {mc/ret-ex-fn no-cache-transient}
  [user-id]
  (api/fetch-user user-id))
```

### `mc/seed`

Pre-populate the cache with initial entries. Map of `[args] -> value`.

```clojure
(m/defmemo get-config
  {mc/seed {[:database-url] "jdbc:..."
            [:api-key] "secret"}}
  [key]
  (load-config key))
```

### `mc/evt-fn`

Event handler for cache events. See [Advanced Features](advanced.md#events) for details.

## Caffeine-Specific Options

Additional options from `memento.caffeine.config` (aliased as `mcc`).

### `mcc/removal-listener`

Callback when entries are evicted. Receives `[id key value cause]`.

```clojure
(require '[memento.caffeine.config :as mcc])

(m/defmemo get-user
  {mc/size< 100
   mcc/removal-listener (fn [id key value cause]
                          (println "Evicted:" key "because" cause))}
  [user-id]
  (db/fetch-user user-id))
```

Causes: `:explicit`, `:replaced`, `:collected`, `:expired`, `:size`

### `mcc/weight<` and `mcc/kv-weight`

Weight-based eviction instead of count-based.

```clojure
{mcc/weight< 10000000  ; Max total weight
 mcc/kv-weight (fn [id key value] 
                 (count (str value)))}  ; Weight = string length
```

### `mcc/weak-values` / `mcc/soft-values`

Use weak or soft references for cached values. Allows GC to reclaim entries under memory pressure.

```clojure
{mcc/weak-values true}  ; GC can reclaim anytime
{mcc/soft-values true}  ; GC reclaims only under memory pressure
```

### `mcc/stats`

Enable cache statistics collection.

```clojure
{mcc/stats true}

;; Later, retrieve stats
(m/stats cached-fn)
```

### `mcc/ticker`

Custom time source for testing. Function returning nanoseconds.

```clojure
{mcc/ticker #(System/nanoTime)}
```

### `mcc/expiry`

Variable per-entry expiry. See [Advanced Features](advanced.md#variable-expiry).

## Configuration Best Practices

### Create Reusable Configurations

```clojure
(ns myapp.cache
  (:require [memento.config :as mc]))

(def short-ttl
  {mc/ttl [1 :m]
   mc/size< 1000})

(def long-ttl
  {mc/ttl [1 :h]
   mc/size< 10000})

(defn request-scoped [& tags]
  {mc/tags (into [:request] tags)})
```

Then use them:

```clojure
(m/defmemo get-user
  (merge cache/long-ttl (cache/request-scoped :user))
  [user-id]
  ...)
```

### Sharing Caches Across Functions

One cache can back multiple functions. This is useful for shared size limits:

```clojure
;; Both functions share the same 10,000 entry limit
(def user-cache (m/create {mc/size< 10000}))

(m/bind #'get-user {} user-cache)
(m/bind #'get-user-preferences {} user-cache)
```

## Utility Functions

```clojure
;; Check if a function is memoized
(m/memoized? get-user)  ; => true

;; Get the original unwrapped function
(m/memo-unwrap get-user)

;; Get cache contents as a map
(m/as-map get-user)  ; => {[1] {:id 1 :name "Alice"}, ...}

;; Get the underlying cache instance
(m/active-cache get-user)

;; Get tags for a memoized function
(m/tags get-user)  ; => [:user :request]
```
