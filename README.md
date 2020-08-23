# Memento

A library for function memoization.

## Dependency

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.roklenarcic/memento.svg)](https://clojars.org/org.clojars.roklenarcic/memento)

## Motivation

Why is there a need for another caching library? [Motivation here.](doc/motivation.md) 

## Usage

**With require as `[memento.core :as m]`:**

You can attach a cache to the function, by wraping it in a `memo` call:

```clojure
(def my-function
  (m/memo #(* 2 %)))
```

This matches a basic memoize.

You can specify cache properties:

```clojure
(def my-function
  (m/memo {:size< 30 :ttl 40} #(* 2 %)))
```

This creates a Guava backed cache that is limited to 30 entries and entries expire after 40 seconds.

Read more about specifying properties [**HERE**](doc/cache-properties.md).

The default implementation is Guava based, and the supported properties
are described [**HERE**](doc/guava-properties.md) 

You can add cache directly to a var:

```clojure
(defn my-function [x] (* 2 x))
 
(m/memo {:size< 30 :ttl 40} #'my-function)
```

If you are using the same settings many times, simply use a variable.

```clojure
; 1 day cache
(def long-cache {:ttl [1 :d]})
 
(m/memo long-cache #'my-function)
```

#### Regions

Normally cache only contains entries pertaining to one function. This presents
a problem when you're trying to limit the size of the whole caching mechanism
or when trying to invalidate large chunks of cached data in the system.

If you have a 100 cached functions, each with `:size< 100`, you allow for 10000 cached items,
but one function might need 1000 items and another one might never need more than 10. In order
to make sizing easier, you can use a cache region.

A Region is a Cache that is shared between many functions and it is named (usually by a keyword).

You can specify that the function uses Region with id `:my-region` as a cache like this:

```clojure
(m/memo :my-region #'my-function)
```

This is equivalent to: 

```clojure
(m/memo {:region :my-region} #'my-function)
```

When using the map form, you can also specify `key-fn`, `ret-fn`, `seed`.

If you specify a region that doesn't exist then no caching is done until the region is registered:

```clojure
(set-region! :my-region {:size< 100})
```

All the functions caching into this region share the same limit of 100 entries.

See more info about regions [**HERE**](doc/regions.md).

#### Scoped regions

You define new regions that are active only within a scope (or alias an existing region).

This is extremely useful (and basically the main reason for this library), e.g.:

```clojure
(m/memo {:region :request-scope} #'my-function)

(with-region :request-scope {}
  ; executes the code with all the :request-scope region functions being cached in a new
  ; region with spec `{}` (infinite cache)
  ...)
; drops all the cached values, as the region created for the scope is dropped
```

You can also redirect caching to an existing region with region aliasing:

```clojure
; cache all functions that use :request-scope region in :small-cache region
(with-region-alias :request-scope :small-cache
  ....)
```

See more info about scopes and regions [**HERE**](doc/regions.md).

#### Skip/disable caching

If the wrapped function (or `:ret-fn` post-processing hook) returns an object wrapped in
`m/non-cached` function, then the result is not cached.

```clojure
(def call-service
  (m/memo
    {:ttl 10}
    (fn [....] ...
      ; don't cache error responses, but still return them
      (if (<= 400 (:status resp)) (m/non-cached resp) resp))))
```

or more commonly via, the `:ret-fn`

```clojure
(def call-service
  (m/memo
    {:ttl 10
     :ret-fn #(if (<= 400 (:status %)) (m/non-cached %) %)}
    (fn [....] ... resp)))
```

If you set `-Dmemento.enabled=false` JVM option, then none of the caches will be used,
(though they will still exist, just empty).

#### Manual eviction

You can manually evict entries:

```clojure
; invalidate everything
(m/memo-clear! memoized-function)
; invaliate an arg-list
(m/memo-clear! memoized-function arg1 arg2 ...)
```

You can manually evict all entries in a region:

```clojure
(m/memo-clear-region! :my-region)
```

#### Manually adding entries

You can add entries to a function's cache at any time:

```clojure
(m/memo-add! memoized-function {[arg1 arg2] result})
```

#### Additional utility

- `(m/as-map memoized-function)` -> map of cache entries
- `(m/region-as-map :my-region)` -> map of region's cache entries
- `(m/memoized? a-function)` -> returns true if the function is memoized
- `(m/memo-unwrap memoized-function)` -> returns `memento.base/Cache` object on function if there is one

## Developer information

- [**Cache spec explained**](doc/cache-properties.md)
- [**Guava cache spec properties**](doc/guava-properties.md)
- [**Regions**](doc/regions.md)
- [**Performance**](doc/performance.md)

## License

Copyright © 2020 Rok Lenarčič

Licensed under the term of the Eclipse Public License - v 2.0, see LICENSE.
