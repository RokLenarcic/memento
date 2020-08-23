# Regions

Normally cache only contains entries pertaining to one function. This presents
a problem when you're trying to limit the size of the whole caching mechanism
or when trying to invalidate large chunks of cached data in the system.

To address that, you can define a Cache region, which is effectively 1 cache shared 
by multiple functions.

If you specify `:memento.cache/region` region id, the function's cache will change into
a RegionCache backed by a region. e.g.:

```clojure
(m/memo {:region :my-region} #'my-function)
```

The function's cache will look up the entry in the region. If `:my-region` doesn't exist, then
the function is effectively uncached (at this time, you can always register the region later).

You can simplify the declaration by specifying the ID directly:

```clojure
(m/memo :my-region #'my-function)
```

You can specify additional properties when declaring the cache on the function:

```clojure
(m/memo {:region :my-region
         :key-fn identity
         :ret-fn identity
         :seed {}}
        #'my-function)
```

These properties have the same semantics as with non-regioned caches, see [**HERE**](cache-properties.md).

Region needs to be created before any caching can happen.

There's three different options:

## 1. Create a new "permanent" region

Calling `memento.core/set-region!` will create and add a region under the specified ID.
If there's an existing region with that ID it will be dropped.

```clojure
(m/set-region! :my-region {:size< 10})
```

The spec for a region is similar as a normal cache takes. Like with
caches, any non-namespaced keys will automatically get converted into `memento.core` namespace.

The basic properties are, as with caches, `:memento.core/type`, `:memento.core/key-fn`, `:memento.core/ret-fn`. 

See [**HERE**](cache-properties.md).

These work as with caches. If both the regional cache and the region have `key-fn` or `ret-fn`, then
both are applied, with regional cache first and region second.

The `type` property selects the region implementation, with `:memento.core/guava` being the default one.

The other properties for the Guava's Region are described [**HERE**](guava-properties.md).

## 2. Create a new region within a scope

You can define a region and bind it to a region ID within a scope.

```clojure
(m/memo {:region :request-scope} #'my-function)

(with-region :request-scope {}
  ; executes the code with all the :request-scope region functions being cached in a new
  ; region with spec `{}` (infinite cache)
  ...)
; drops all the cached values, as the region created for the scope is dropped
```

Within `with-region` scope, function using `:request-scope` region with use a new region with spec `{}`.
This region is dropped after the scope exits. You can override existing region with this, or more often,
define create a region for an ID than normally has none.

## 3. Alias an existing region to another region within a scope

This is useful when you want a region to use different cache in different scopes.

```clojure
(m/memo {:region :request-scope} #'my-function)
(m/set-region! :short-cache {:ttl [10 :s]})
(m/set-region! :long-cache {:ttl [10 :d]})
(with-region-alias :request-scope :long-cache
  ; uses and save entries in `long-cache` 
  ...)
```
