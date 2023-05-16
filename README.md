# Memento

A library for function memoization with scoped caches and tagged eviction capabilities.

## Dependency

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.roklenarcic/memento.svg)](https://clojars.org/org.clojars.roklenarcic/memento)

## Version 1.0 breaking changes

Version 1.0 represents a switch from Guava to Caffeine, which is a faster caching library, with added
benefit of not pulling in the whole Guava artefact which is more that just that Cache. The Guava Cache type
key and the config namespace are deprecated and will be removed in the future.

## Caffeine version notice

This library uses Caffeine 2.9.3 as dependency as that version is Java 8 compatible. If you are using
Java 11 or better you should use it with Caffeine 3.x.

## Motivation

Why is there a need for another caching library?

- request scoped caching (and other scoped caching)
- eviction by secondary index
- disabling cache for specific function returns
- tiered caching
- size based eviction that puts limits around more than one function at the time
- cache events

## Adding cache to a function

**With require `[memento.core :as m][memento.config :as mc]`:**

Define a function + create new cache + attach cache to a function:

```clojure
(m/defmemo my-function
  {::m/cache {mc/type mc/caffeine}}
  [x]
  (* 2 x))
```

### **The key parts here**:
- `defmemo` works just like `defn` but wraps the function in a cache 
- specify the cache configuration via `:memento.core/cache` keyword in function meta

Quick reminder, there are two ways to provide metadata when defining functions: `defn` allows a meta
map to be provided before the argument list, or you can add meta to the symbol directly as supported by the reader:

```clojure
(m/defmemo ^{::m/cache {mc/type mc/caffeine}} my-function
  [x]
  (* 2 x))
```

### Caching an anonymous function

You can add cache to a function object (in `clojure.core/memoize` fashion):

```clojure
(m/memo (fn [] ...) {mc/cache mc/caffeine})
```

### Other ways to attach Cache to a function

[Caches and memoize calls](doc/major.md)

## Cache conf(iguration)

See above: `{mc/type mc/caffeine}`

The cache conf is an open map of namespaced keywords such as `:memento.core/type`, various cache implementations can
use implementation specific config keywords.

Learning all the keywords and what they do can be hard. To assist you 
there are special conf namespaces provided where conf keywords are defined as vars with docs,
so it's easy so you to see which configuration keys are available and what their function is. It also helps
prevent bugs from typing errors.

The core properties are defined in `[memento.config :as mc]` namespace. Caffeine specific properties are defined
in `[memento.caffeine.config :as mcc]`. 

Here's a couple of equal ways of writing out you cache configuration meta:

```clojure
; the longest
{:memento.core/cache {:memento.core/type :memento.core/caffeine}}
; using alias
{::m/cache {::m/type ::m/caffeine}}
; using memento.config vars - recommended
{mc/cache {mc/type mc/caffeine}}
```

### Core conf

The core configuration properties:

#### mc/type

Cache implementation type, e.g. caffeine, redis, see the implementation library docs. **Make sure
you load the implementation namespace at some point!**. Caffeine namespace is loaded automatically
when memento.core is loaded.

#### mc/size<

Size limit expressed in number of entries or total weight if implementation supports weighted cache entries

#### mc/ttl

Entry is invalid after this amount of time has passed since its creation

It's either a number (of seconds), a pair describing duration e.g. `[10 :m]` for 10 minutes,
see `memento.config/timeunits` for timeunits.

#### mc/fade

Entry is invalid after this amount of time has passed since last access, see `mc/ttl` for duration
specification.

#### mc/key-fn, mc/key-fn*

Specify a function that will transform the function arg list into the final cache key. Used 
to drop function arguments that shouldn't factor into cache entry equality.

The `key-fn` receives a sequence of arguments, `key-fn*` receives multiple arguments as if it
was the function itself.

See: [Changing the key for cached entry](doc/key-fn.md)

#### mc/ret-fn

A function that is called on every cached function return value. Used for general transformations
of return values.

#### mc/seed

Initial entries to load in the cache.

#### mc/initial-capacity

Cache capacity hint to implementation.

## Conf is a value (map)

Cache conf can get quite involved:

```clojure
(ns memento.tryout
  (:require [memento.core :as m]
    ; general cache conf keys
            [memento.config :as mc]
    ; caffeine specific cache conf keys
            [memento.caffeine.config :as mcc]))

(def my-weird-cache
  "Conf for caffeine cache that caches up to 20 seconds and up to 30 entries, uses weak
  references and prints when keys get evicted."
  {mc/type mc/caffeine
   mc/size< 30
   mc/ttl 20
   mcc/weak-values true
   mcc/removal-listener #(println (apply format "Function %s key %s, value %s got evicted because of %s" %&))})

(m/defmemo my-function
  {::m/cache my-weird-cache}
  [x] (* 2 x))
```

Seeing as cache conf is a map, I recommend a pattern where you have a namespace in your application that contains vars
with your commonly used cache conf maps and functions that generate slightly parameterized
configuration. E.g.

```clojure
(ns my-project.cache
  (:require [memento.config :as mc]))

;; infinite cache
(def inf-cache {mc/type mc/caffeine})

(defn for-seconds [n] (assoc inf-cache mc/ttl n))
```

Then you just use that in your code:

```clojure
(m/defmemo my-function
  {::m/cache (cache/for-seconds 60)}
  [x] (* x 2))
```

## Caches and mount points

Enabling memoization of a function is composed of two distinct steps:

- creating a Cache (optional, as you can use an existing cache)
- binding the cache to the function (a MountPoint is used to connect a function being memoized to the cache)

A cache, an instance of memento.base/Cache, can contain entries from multiple functions and can be shared between memoized functions.
Each memoized function is bound to a Cache via MountPoint. When you call a function such as `(m/as-map a-cached-function)` you are 
operating on a MountPoint.

The reason for this separation is two-fold:

#### 1. **Improved Size Based Eviction**

So far all examples implicitly created a new cache for each memoized function, but if we use same cache for multiple 
functions, then any size based eviction will apply to them as a whole. If you have 100 memoized functions, and you want to
somewhat limit their memory use, what do you do? In a typical cache library you might limit each of them to 100 entries. So you
allocated 10000 slots total, but one function might have an empty cache, while a very heavily used one needs way more than 100
slots. If all 100 function are backed by same Cache instance with 10000 slots then they automatically balance themselves out.

#### 2. **Changing cache temporarily to allow for scoped caching**

This indirection with Mount Points allows us to change which cache is backing a function dynamically. See discussion of tagged
caches below. Here's an example of using tags when caching and scoped caching

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

## Additional features

#### [Prevent caching of a specific return value (and general return value xform)](doc/ret-fn.md)

#### [Manually add or evict entries](doc/manual-add-remove.md)

#### `(m/as-map memoized-function)` to get a map of cache entries, also works on MountPoint instances
#### `(m/memoized? a-function)` returns true if the function is memoized
#### `(m/memo-unwrap memoized-function)` returns original uncached function, also works on MountPoint instances
#### `(m/active-cache memoized-function)` returns Cache instance from the function, if present.

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

Version 1.0.x changed implementation from Guava to Caffeine
Version 0.9.0 introduced many breaking changes.

## License

Copyright © 2020-2021 Rok Lenarčič

Licensed under the term of the MIT License, see LICENSE.
