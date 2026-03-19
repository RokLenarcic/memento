# Memento

A Clojure memoization library with **scoped caching** and **smart invalidation**.

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.roklenarcic/memento.svg)](https://clojars.org/org.clojars.roklenarcic/memento)

## Why Memento?

`clojure.core/memoize` and `clojure.core.memoize` provide basic caching, but real applications need:

- **Scoped caching** - fresh cache per request/job/test, discarded when done (scopes can nest)
- **Tag-based invalidation** - clear all cached data for an entity with one call  
- **N+1 query prevention** - populate single-item caches from bulk loads
- **[Variable per-entry expiry](doc/advanced.md#variable-expiry)** - set TTL based on the cached value itself
- **[2-3x better performance](doc/performance.md)** - backed by Caffeine

### Scoped Caching

Traditional caching strategies struggle with API/web requests:

- **TTL-based caching**: What timeout do you pick? Too short and you get no benefit. Too long and users see stale data. There's rarely a "right" answer because data can change at any moment.
- **Size-based caching**: Prevents memory issues but doesn't help with staleness. A cached value could be seconds or hours old.

What you actually want for request handling:
- Start fresh for each request (no stale data from previous requests)
- Cache within the request (avoid repeated DB calls in the same request)
- Discard when the request ends (no memory leaks)

Memento's `with-caches` makes this trivial. While request handling is the most common use case, scopes work for any bounded context - background jobs, batch processing, test fixtures - and can be nested.

### Smart Invalidation

When a user updates their profile, you need to invalidate all cached data about that user - across multiple functions. Memento's tag-based invalidation lets you do this with a single call:

```clojure
(m/memo-clear-tag! :user user-id)  ; Clears user 123's data from ALL tagged caches
```

### N+1 Query Prevention

You have single-item cached functions like `get-user-email`. Elsewhere you load a list of 100 users. Without coordination, calling `get-user-email` for each user means 100 database queries - for data you already have.

Memento's event system lets bulk loaders populate single-item caches:

```clojure
;; After loading users in bulk, fire events to populate individual caches
(doseq [user users]
  (m/fire-event! :user [:user-seen user]))
```

See [Events documentation](doc/advanced.md#events-n1-query-prevention) for the full pattern.

## Installation

```clojure
;; deps.edn
org.clojars.roklenarcic/memento {:mvn/version "2.0.68"}

;; Leiningen
[org.clojars.roklenarcic/memento "2.0.68"]
```

Requires Java 11+.

## Quick Start

```clojure
(require '[memento.core :as m]
         '[memento.config :as mc])

;; Basic memoization - wrap your function with a cache
(def get-user
  (m/memo (fn [user-id]
            (println "Fetching user" user-id)
            {:id user-id :name "Alice"})
          {mc/type mc/caffeine}))  ; Use Caffeine cache

(get-user 1)  ; prints "Fetching user 1", returns {:id 1 :name "Alice"}
(get-user 1)  ; returns cached result, no print
```

### Using `defmemo`

`defmemo` works just like `defn` - the map is standard function metadata:

```clojure
(require '[memento.core :as m]
         '[memento.config :as mc])

(m/defmemo get-user
  "Fetches user from database."
  {mc/type mc/caffeine}
  [user-id]
  (db/fetch-user user-id))
```

### With Size and Time Limits

```clojure
(m/defmemo get-user
  "Fetches user, cached for 5 minutes, max 1000 entries."
  {mc/type mc/caffeine
   mc/size< 1000
   mc/ttl [5 :m]}
  [user-id]
  (db/fetch-user user-id))
```

**Note**: Include `mc/type mc/caffeine` for functions that should always cache. For request-scoped caching, you can omit the type (see below).

## Common Use Cases

### Cache with Time Expiration

Data goes stale. Set a TTL (time-to-live) to automatically expire entries:

```clojure
(m/defmemo get-exchange-rate
  "Cache exchange rates for 1 minute."
  {mc/type mc/caffeine
   mc/ttl [1 :m]}    ; Also: [30 :s], [2 :h], [1 :d]
  [currency]
  (api/fetch-rate currency))
```

Or use `fade` for access-based expiration (expires if not accessed):

```clojure
(m/defmemo get-user-preferences
  "Cache preferences, expire after 10 minutes of no access."
  {mc/type mc/caffeine
   mc/fade [10 :m]}
  [user-id]
  (db/fetch-preferences user-id))
```

### Limit Cache Size

Prevent unbounded memory growth with size limits:

```clojure
(m/defmemo get-product
  "Cache up to 10,000 products (LRU eviction)."
  {mc/type mc/caffeine
   mc/size< 10000}
  [product-id]
  (db/fetch-product product-id))
```

### Scoped Caching

Use `with-caches` to temporarily replace caches for tagged functions within a scope:

```clojure
;; Option 1: No caching outside scope (tags only, no mc/type)
(m/defmemo get-user
  {mc/tags [:request]}
  [user-id]
  (db/fetch-user user-id))

;; Option 2: Long-term cache outside scope, fresh cache inside
(m/defmemo get-user-orders
  {mc/type mc/caffeine
   mc/ttl [1 :h]
   mc/tags [:request]}
  [user-id]
  (db/fetch-orders user-id))

;; In your request handler middleware
(defn wrap-request-cache [handler]
  (fn [request]
    (m/with-caches :request
      (constantly (m/create {mc/type mc/caffeine}))  ; Fresh cache for this scope
      (handler request))))
      
;; Within a request:
;; - Both functions use the fresh scoped cache
;; - Multiple calls with same args hit the cache
;; - Cache is discarded when scope ends
```

See the [Scoped Caching Guide](doc/scoped-caching.md) for more patterns including nested scopes and consulting long-term caches.

### Custom Cache Keys

By default, the cache key is the full argument list. Use `mc/key-fn` to transform it:

```clojure
;; Ignore the db-conn argument for caching purposes
(m/defmemo get-user
  {mc/type mc/caffeine
   mc/key-fn rest}  ; Cache key is [user-id], not [db-conn user-id]
  [db-conn user-id]
  (db/fetch-user db-conn user-id))

;; Extract a nested value from a request map
(m/defmemo get-current-user
  {mc/type mc/caffeine
   mc/key-fn* (fn [request] (-> request :session :user-id))}
  [request]
  (db/fetch-user (-> request :session :user-id)))
```

`mc/key-fn` receives args as a sequence; `mc/key-fn*` receives them as separate parameters (like the function itself).

### Transform Return Values

Use `mc/ret-fn` to transform values before caching, or prevent caching certain values:

```clojure
;; Don't cache error responses
(m/defmemo fetch-api-data
  {mc/type mc/caffeine
   mc/ret-fn (fn [args response]
               (if (>= (:status response 0) 400)
                 (m/do-not-cache response)  ; Don't cache errors
                 response))}
  [endpoint]
  (http/get endpoint))
```

### Invalidate When Data Changes

Without tag-based invalidation, you face an N×M maintenance problem:
- You have **N cached functions** that read user data
- You have **M functions** that modify user data
- Every modifier must know about every cached function to invalidate it
- Adding a new cached function means updating all M modifiers
- Adding a new modifier means knowing all N cached functions

Tag-based invalidation decouples them completely:

```clojure
;; CACHED FUNCTIONS: just tag with :user, don't care who invalidates
(m/defmemo get-user
  {mc/type mc/caffeine, mc/tags [:user]}
  [user-id]
  (-> (db/fetch-user user-id)
      (m/with-tag-id :user user-id)))

(m/defmemo get-user-orders  
  {mc/type mc/caffeine, mc/tags [:user]}
  [user-id]
  (-> (db/fetch-orders user-id)
      (m/with-tag-id :user user-id)))

(m/defmemo get-user-preferences
  {mc/type mc/caffeine, mc/tags [:user]}
  [user-id]
  (-> (db/fetch-preferences user-id)
      (m/with-tag-id :user user-id)))

;; MODIFYING FUNCTIONS: just invalidate :user tag, don't care who's cached
(defn update-user! [user-id data]
  (db/update-user! user-id data)
  (m/memo-clear-tag! :user user-id))

(defn delete-user! [user-id]
  (db/delete-user! user-id)
  (m/memo-clear-tag! :user user-id))

(defn merge-users! [from-id to-id]
  (db/merge-users! from-id to-id)
  (m/memo-clear-tags! [:user from-id] [:user to-id]))
```

Now you can add cached functions or modifying functions independently - they only need to agree on the tag name (`:user`).

A cached value can also be tagged with **multiple IDs** - useful for aggregated data like dashboards. See the [Invalidation Guide](doc/invalidation.md) for details.

### Manually Clear Cache

```clojure
;; Clear all entries for a function
(m/memo-clear! get-user)

;; Clear specific entry
(m/memo-clear! get-user 123)
```

## Configuration Reference

### Time Units

Durations can be numbers (seconds) or `[amount :unit]` pairs:

```clojure
30        ; 30 seconds
[30 :s]   ; 30 seconds
[5 :m]    ; 5 minutes
[2 :h]    ; 2 hours
[1 :d]    ; 1 day
```

### Common Settings

| Setting | Description | Example |
|---------|-------------|---------|
| `mc/type` | Cache implementation (**required**) | `mc/caffeine` |
| `mc/size<` | Max entries (LRU eviction) | `1000` |
| `mc/ttl` | Time-to-live | `[5 :m]` |
| `mc/fade` | Expire after last access | `[10 :m]` |
| `mc/tags` | Tags for scoping/invalidation | `[:user :request]` |
| `mc/key-fn` | Transform args to cache key | `(fn [args] ...)` |
| `mc/ret-fn` | Transform return value | `(fn [args val] ...)` |

See [Configuration Guide](doc/configuration.md) for all options.

## Further Documentation

- **[Configuration Guide](doc/configuration.md)** - All configuration options, `key-fn`, `ret-fn`
- **[Invalidation Guide](doc/invalidation.md)** - Cache clearing, tag-based invalidation
- **[Scoped Caching Guide](doc/scoped-caching.md)** - `with-caches`, nested scopes, request patterns
- **[Advanced Features](doc/advanced.md)** - Tiered caching, events, variable expiry
- **[Performance](doc/performance.md)** - Benchmarks and comparisons
- **[Internals](doc/internals.md)** - Architecture for contributors

## Disabling Caching

For testing or debugging, disable all caching globally:

```bash
java -Dmemento.enabled=false ...
```

## Migration

See [MIGRATION.md](MIGRATION.md) for version upgrade guides.

## License

Copyright 2020-2024 Rok Lenarcic

Licensed under the MIT License.
