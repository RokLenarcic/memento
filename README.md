# Memento

A library for function memoization with scoped caches and tagged eviction capabilities.

## Dependency

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.roklenarcic/memento.svg)](https://clojars.org/org.clojars.roklenarcic/memento)

## Motivation

Why is there a need for another caching library?

- request scoped caching (and other scoped caching)
- eviction by secondary index
- disabling cache for specific function returns
- tiered caching
- size based eviction that puts limits around more than one function at the time
- cache events

## Usage

**With require as `[memento.core :as m]`:**

You can attach a cache to the function, by wrapping it in a `memo` call:

```clojure
(def my-function (m/memo #(* 2 %) {}))
```

The first argument is the function (or a var), and the second one is the cache conf.

If a var is specified, the root binding of the var is modified to the cached function.

```clojure
(defn my-function [x] (* 2 x))
(m/memo #'my-function {})
```

**The cache conf is a plain map. Use variables and normal map operations to construct these.**

The `{}` conf results in the default cache being created, which is a cache that does no caching (this is useful for reasons listed later).

But if we want a cache that does actual caching, we can create an infinite duration cache implemented by Guava:

```clojure
(m/memo #'my-function {:memento.core/type :memento.core/guava})
; or using the namespace shorthands
(m/memo #'my-function #::m {:type ::m/guava})
```

Such cache works just like `clojure.core/memoize`, a memoization cache with unlimited duration and size.

We have specified cache implementation type to be guava (instead of `:memento.core/none` which is a noop cache type).

Guava is the main implementation provided by this library. 

Guava type takes additional parameters to customize behaviour:

```clojure
(m/memo  #'my-function #::m {:type ::m/guava 
                             :ttl [40 :min]})
```

It can be cumbersome to remember all these properties and to type them out.

For this purpose, and for purpose of documentation, there are special configuration namespaces
with vars that are conf keys. The docstrings explain the settings.

Here's an example of a complicated cache conf:

```clojure
(ns memento.tryout
  (:require [memento.core :as m]
    ; general cache conf keys
            [memento.config :as mc]
    ; guava specific cache conf keys
            [memento.guava.config :as mcg]))

(def my-weird-cache
  "Conf for guava cache that caches up to 20 seconds and up to 30 entries, uses weak
  references and prints when keys get evicted."
  {mc/type mc/guava
   mc/size< 30
   mc/ttl 20
   mcg/weak-values true
   mcg/removal-listener #(println (apply format "Function %s key %s, value %s got evicted because of %s" %&))})

(defn my-function [x] (* 2 x))
(m/memo #'my-function my-weird-cache)
```

Read doc strings in `memento.config` and `memento.guava.config` namespaces for available on cache properties.

**I suggest you collect cache configurations you commonly use in a namespace
and reuse them in your code, to keep the code brief.**

Create a namespace like `myproject.cache` and write vars like:
```clojure
(def inf {mc/type mc/guava}) ; infinite cache
```

and then simply use it everywhere in your project:

```clojure
(ns myproject.some-ns
  (:require [myproject.cache :as cache]
            [memento.core :as m]))

; simply use value for conf 
(m/memo #'myfunction cache/inf)
```

Another example using tags, scoped caches and tagged eviction:

```clojure
(ns myproject.some-ns
  (:require [myproject.cache :as cache]
            [memento.core :as m]))

(defn get-person-by-id [person-id]
  (let [person (db/get-person person-id)]
    ; tag the returned object with :person + id pair
    (m/with-tag-id person :person (:id person))))

; add a cache to the function with tags :person and :request
(m/memo #'get-person-by-id [:person :request] cache/inf)

; remove cache entries from every cache tagged :person globally, where the
; entry is tagged with :person 1
(m/memo-clear-tag! :person 1)

(m/with-caches :request (constantly (m/create cache/inf))
  ; inside this block, a fresh new cache is used (and discarded)
  ; making a scope-like functionality
  (get-person-by-id 5))
```

## Major concepts (cache, bind and mount point)

Enabling memoization of a function is composed of two distinct steps:

- creating a Cache (optional, as you can use an existing cache)
- binding the cache to the function (a MountPoint is used to connect a function being memoized to the cache)

[Read about these core concepts here.](doc/major.md)

## Additional features

#### [Changing the key for cached entry](doc/key-fn.md)

#### [Prevent caching of a specific return value (and general return value xform)](doc/ret-fn.md)

#### [Manually add or evict entries](doc/manual-add-remove.md)

#### `(m/as-map memoized-function)` to get a map of cache entries, also works on MountPoint instances
#### `(m/memoized? a-function)` returns true if the function is memoized
#### `(m/memo-unwrap memoized-function)` returns original uncached function, also works on MountPoint instances
#### `(m/active-cache memoized-function)` returns Cache instance from the function, if present.

#### Additional utility

- `(m/as-map memoized-function)` -> map of cache entries, also works on MountPoint instances
- `(m/memoized? a-function)` -> returns true if the function is memoized
- `(m/memo-unwrap memoized-function)` -> returns original uncached function, also works on MountPoint instances

## Tags 

You can add tags to the caches. Tags enable that you:

- run actions on caches with specific tags
- **change or update cache of tagged MountPoints within a scope**
- change or update cache of tagged MountPoints permanently
- use secondary index to invalidate entries by a tag + ID pair

This is a very powerful feature, [read more here.](doc/tags.md)

## Namespace scan

You can scan loaded namespaces for annotated vars and automatically create caches.

[Read more](doc/ns-scan.md)

## Events

You can fire an event at a memoized function. Main use case is to enable adding entries to different functions from same data.

[Read more](doc/events.md)

## Tiered caching

You can use caches that combine two other caches in some way. The easiest way to generate
the cache configuration needed is to use `memento.core/tiered`,`memento.core/consulting`, `memento.core/daisy`.

[Read more](doc/tiered.md)

## if-cached

memento.core/if-cache is like an if-let, but the "then" branch executes if the function call
is cached, otherwise else branch is executed. The binding is expected to be a cached function call form, otherwise 
an error is thrown. 

Example:

```clojure
(if-cached [v (my-function arg1)]
  (println "cached value is " v)
  (println "value is not cached"))
```

## Skip/disable caching

If you set `-Dmemento.enabled=false` JVM option (or change `memento.config/enabled?` var root binding), 
then type of all caches created will be `memento.base/no-cache`, which does no caching. 

## Reload guards

When you memoize a function with tags, a special object is created that will clean up in internal tag
mappings when memoized function is GCed. It's important when reloading namespaces to remove mount points
on the old function versions.

It uses finalize, which isn't free (takes extra work to allocate and GC has to work harder), so
if you don't use namespace reloading, and you want to optimize you can disable reload guard objects.

Set `-Dmemento.reloadable=false` JVM option (or change `memento.config/reload-guards?` var root binding).

## Developer information

- [**Performance**](doc/performance.md)

## Breaking changes

Version 0.9.0 introduced many breaking changes.

## License

Copyright © 2020-2021 Rok Lenarčič

Licensed under the term of the Eclipse Public License - v 2.0, see LICENSE.
