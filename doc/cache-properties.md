# Cache spec

Cache is configured by a map. Put settings that you use often
into a variable to reuse them.

**With require as `[memento.core :as m]`:**

```clojure
(def long-cache {:ttl 30})
(def short-cache {:ttl 1})
(m/memo long-cache my-function)
```

The keys that are non-namespaced are automatically added the `memento.core` namespace.

So these specs are equivalent:
```clojure
{:ttl 30}
{:memento.core/ttl 30}
{::m/ttl 30}
#::m {:ttl 30}
#:memento.core {:ttl 30}
```

The basic properties of caches are:

### 1. `memento.core/type`

This property selects the cache implementation. If it's not supplied
then the value of `memento.core/*default-type*` is used, which is by default `:memento.core/guava`.

This library has two implementations: `:memento.core/guava` and `memento.core/regional`,
and since the latter is automatically selected where appropriate, this property is usually omitted.

### 2. `memento.core/region`

This sets the region for the cache. [**Read all about regions here**](regions.md).
This also forces `:memento.core/type` to `:memento.core/regional`.

### 3. `memento.core/seed`

A map of argument lists to results that is inserted into the cache as it is created.

```clojure
; preload some sums
(m/memo {:seed {[2 3] 5 [1 4] 5}} +)
```

### 4. `memento.core/key-fn`

A function of 1 argument that is used to transform the argument list into the cache key.

Default is `identity`.

**In documentation, the value before `key-fn` is applied is called the *"argument list"* and after
is called *"transformed key"*.**

This is used to drop (or add) to the key. e.g.:

```clojure
(defn get-person-by-id [db-conn person-id] ...)
; drop the db-conn from the key
(m/memo {:key-fn rest} #'get-person-by-id)
```

Obviously we don't want the cache to hold reference to db-conn or vary the cache by it.

There's other neat things you can do with this:
```clojure
(defn get-people-map [& id-list] ...)
; only distinct IDs matter and order doesn't matter
(m/memo {:key-fn set} #'get-people-map)
```

When using a regional cache, first the `key-fn` of cache spec is applied, then
the `key-fn` of the region; see [**regions**](regions.md).

**When talking about original function arguments we will use term `args` or `arguments`

### 5. `memento.core/ret-fn`

A function of 1 argument that is used to transform the result of a function call before it is cached.

Default is `identity`.

**In documentation, the value before `ret-fn` is applied is called the *"result"* and after
is called *"transformed result"*.**

It can be used to prevent caching with the use of `memento.core/non-cached` function.

```clojure
(def call-service
  (m/memo
    {:ttl 10
     ; don't cache error responses
     :ret-fn #(if (<= 400 (:status %)) (m/non-cached %) %)}
    (fn [....] ... resp)))
```

## More properties

Other properties are passed to the Cache implementation and are implementation specific.

For the Guava cache you can find them [**here**](guava-properties.md).
