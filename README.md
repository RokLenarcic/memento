# Memento

A library for function memoization with scoped caches and tagged eviction capabilities.

## Dependency

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.roklenarcic/memento.svg)](https://clojars.org/org.clojars.roklenarcic/memento)

## Motivation

Why is there a need for another caching library? [Motivation here.](doc/motivation.md) 

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

**The cache conf is a plain map. Use variables and normal map configurations to construct these.**

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
with vars are conf keys. The docstrings explain the settings.

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

## Major concepts

Back to basics.
Enabling memoization of a function is composed of two distinct steps:

- creating a Cache (optional, as you can use an existing cache)
- binding the cache to the function (a MountPoint is used to connect a function being memoized to the cache)

A cache, an instance of memento.base/Cache, can contain entries from multiple functions and can be shared between memoized functions.
Each memoized function is bound to a Cache via MountPoint.

#### Creating a cache

Creating a cache is done by using `memento.core/create`, which takes a map of configuration (called **cache conf**).
You can use the resulting Cache with multiple functions. The configuration properties (map keys) can be found
in `memento.config` and `memento.guava.config`, look for "Cache setting" in docstring.

If `memento.config/enabled?` is false, this function always returns `memento.base/no-cache`, which is a Cache
implementation that doesn't do any caching. You can set this at start-up by specifying java property:
`-Dmemento.enabled=false`.

#### Binding the cache

Binding the cache to a function is done by `memento.core/bind`. Parameters are:

- a fn or a var, if var, the root value of var is changed to a memoized version
- a mount point configuration or **mount conf** for short
- a Cache instance that you want to bind

Mount conf is either a map of mount point configuration properties, or a shorthand (see below).
The configuration properties (map keys) can be found in `memento.config`, look for "function bind" in docstring.

Instead of map of properties, **mount conf** can be a shorthand, which has the following two shorthands:
- `[:some-keyword :another-keyword]` -> `{:memento.core/tags [:some-keyword :another-keyword]}`
- `:a-keyword` -> `{:memento.core/tags [:a-keyword]}`

#### Create + bind combined

You can combine both functions into 1 call using `memento.core/memo`.

```clojure
(m/memo fn-or-var mount-conf cache-conf)
```

To make things shorter, there's a 2-arg variant that allows that you specify both configurations at once:

```clojure
(m/memo fn-or-var conf)
```

If conf is a map, then all the properties valid for mount conf are treated as such. The rest is passed to cache create.
If conf is a mount conf shorthand then cache conf is considered to be {}. E.g.

```clojure
(m/memo my-fn :my-tag)
```

This creates a memoized function tagged with `:my-tag` bound to a cache that does no caching.

## Additional features

#### Changing cache key

Add `:memento.core/key-fn` to cache or mount config (or use `mc/key-fn` value) to specify a function with which to manipulate
the key cache will use for the entry. 

Example building on previous suggested `cache/inf` cache configuration:

```clojure
(defn get-person-by-id [db-conn account-id person-id] {})

; when creating the cache key, remove db connection
(m/memo #'get-person-by-id (assoc cache/inf-cache mc/key-fn #(remove db-conn? %)))
; or more explicit
(m/memo #'get-person-by-id {mc/type mc/guava mc/key-fn #(remove db-conn? %)})
```

When creating the cache key, remove db connection, so the cache uses `[account-id person-id]` as key.
Thus calling the function with different db connection but same ids returns the cached value.

**This is both a mount conf setting, and a cache setting.** The obvious difference is that specifying
`key-fn` for the Cache will affect all functions using that cache and in mount conf, only that one function will
be affected. If using 2-arg `memo`, then this setting is applied to mount conf.

#### Prevent caching of a specific return value

If you want to prevent caching of a specific function return, you can wrap it in special record
using `memento.core/do-not-cache` function. Example:

```clojure
(defn get-person-by-id [db-conn account-id person-id]
  (if-let [person (db-get-person db-conn account-id person-id)]
    {:status 200 :body person} 
    (m/do-not-cache {:status 404})))
```

404 responses won't get cached, and the function will be invoked every time for those ids.

#### Modifying returned value

Sticking a piece of caching logic into your function logic isn't very clean. Instead, you can 
add `:memento.core/ret-fn` to cache or mount conf (or use `mc/ret-fn` value) to specify a function that can modify
the return value from a cached function before it is cached. This is useful when using the `do-not-cache` function above to
do the wrapping outside the function being cached. Example:

```clojure
; first argument is args, second is the returned value
(defn no-cache-error-resp [[db-conn account-id person-id :as args] resp]
  (if (<= 400 (:status resp) 599)
    (m/do-not-cache resp)
    resp))

(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    {:status 200}))

(m/memo #'get-person-by-id (assoc cache/inf-cache mc/ret-fn no-cache-error-resp))
```

**This is both a mount conf setting, and a cache setting. This has same consequences as with key-fn setting above.**

#### Manual eviction

You can manually evict entries:

```clojure
; invalidate everything, also works on MountPoint instances
(m/memo-clear! memoized-function)
; invaliate an arg-list, also works on MountPoint instances
(m/memo-clear! memoized-function arg1 arg2 ...)
```

You can manually evict all entries in a Cache instance:

```clojure
(m/memo-clear-cache! cache-instance)
```

#### Manually adding entries

You can add entries to a function's cache at any time:

```clojure
; also works on MountPoint instances
(m/memo-add! memoized-function {[arg1 arg2] result})
```

#### Additional utility

- `(m/as-map memoized-function)` -> map of cache entries, also works on MountPoint instances
- `(m/memoized? a-function)` -> returns true if the function is memoized
- `(m/memo-unwrap memoized-function)` -> returns original uncached function, also works on MountPoint instances

## Tags

You can add tags to the caches. You can run actions on caches with specific tags.

You can specify them via `:memento.core/tags` key (also `mc/tags` value),
or you can simply specify them instead of conf map, which creates a tagged cache
of noop type (that you can replace later).

```clojure
(m/memo {mc/tags [:request-scope :person]} #'get-person-by-id)
(m/memo [:request-scope :person] #'get-person-by-id)
(m/memo :person #'get-person-by-id)
```

#### Utility

You can fetch tags on a memoized function.

```clojure
(m/tags get-person-by-id)
=> [:person]
```

You can fetch all mount points of functions that are tagged by a specific tag:

```clojure
(m/mounts-by-tag :person)
=> #{#memento.mount.TaggedMountPoint{...}}
```

#### Change / update cache within a scope

```clojure
(m/with-caches :person (constantly (m/create cache/inf-cache))
  (get-person-by-id db-spec 1 12)
  (get-person-by-id db-spec 1 12)
  (get-person-by-id db-spec 1 12))
```

Every memoized function (mountpoint) inside the block has its cache updated to the result of the
provided function. In this example, all the `:person` tagged functions will use the same unbounded cache
within the block. This effectively stops them from using any previously cached values and any values added to
cache are dropped when block is exited. 

**This is extremely useful to achieve request scoped caching.**

#### Updating / changing cache instance permanently

You can update Cache instances of all functions tagged by a specific tag. This will modify root binding
if not inside `with-caches`, otherwise it will modify the binding.

```clojure
(m/update-tag-caches! :person (constantly (m/create cache/inf-cache)))
```

All `:person` tagged memoized functions will from this point on use a new empty unbounded cache.

#### Applying operations to tagged memoized functions

Use `mounts-by-tag` to grab mount points and then apply any of the core functions to them.

```clojure
(doseq [f (m/mounts-by-tag :person)]
  (m/memo-clear! f))
```

#### Invalidate entries by a tag + ID combo

You can add tag + ID pairs to cached values. This can be later used to invalidate these
entried based on that ID.

ID can be a number like `1` or something complex like a `[1 {:region :us}]`. You can attach multiple
IDs for same tag.

You can add the tag ID pair inside the cached function or in the ret-fn:

```clojure
(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    (-> {:status 200}
        (m/with-tag-id :person person-id)
        (m/with-tag-id :account account-id))))

(m/memo #'get-person-by-id [:person :account] cache/inf-cache)
```

Now you can invalidate all entries linked to a specified ID in any correctly tagged cache:

```clojure
(m/memo-clear-tag! :account 1)
```

This will invalidate entries with tag id `:account, 1` in all `:account` tagged functions.

As mentioned, you can move code that adds the id information to a `ret-fn`:

```clojure
; first argument is args, second is the returned value
(defn ret-fn [[_ account-id person-id :as args] resp]
  (if (<= 400 (:status resp) 599)
    (m/do-not-cache resp)
    (-> resp
        ; we can grab the data from arg list
        (m/with-tag-id :account account-id)
        (m/with-tag-id :person person-id)
        ; or we can grab it from the return value
        (m/with-tag-id :person (:id resp)))))

(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    {:status 200 :id person-id :name ....}))

(m/memo #'get-person-by-id [:person :account] (assoc cache/inf-cache mc/ret-fn ret-fn))
```

Later you can invalidate tagged entries:

```clojure
(m/memo-clear-tag! :person 1)
```

#### Namespace scan

You can scan loaded namespaces for annotated vars and automatically create caches.
The scan looks for Vars with `:memento.core/cache` key in the meta.
That value is used as a cache spec.

Given require `[memento.ns-scan :as ns-scan]`:
```clojure
(ns myproject.some-ns
  (:require 
    [myproject.cache :as cache]
    [memento.core :as m]))

; defn already has a nice way for adding meta
(defn test1
  "A function using built-in defn meta mechanism to specify a cache region"
  {::m/cache cache/inf}
  [arg1 arg2]
  (+ arg1 arg2))

; you can also do standard meta syntax
(defn ^{::m/cache cache/inf} test2
  "A function using normal meta syntax to add a cache to itself"
  [arg1 arg2] (+ arg1 arg2))

; this also works on def
(def ^{::m/cache cache/inf} test3 (fn [arg1 arg2] (+ arg1 arg2)))

; attach caches
(ns-scan/attach-caches)
```

This only works on LOADED namespaces, so beware.

Calling `attach-caches` multiple times attaches new caches, replaces existing caches.

Namespaces `clojure.*` and `nrepl.*` are not scanned by default, but you can
provide your own blacklists, see doc.

#### Skip/disable caching

If you set `-Dmemento.enabled=false` JVM option (or change `memento.config/enabled?` var root binding), 
then all caches created will `memento.base/no-cache`, which does no caching. 

#### Reload guards

When you memoize a function with tags, a special object is created that will clean up in internal tag
mappings when memoized function is GCed. It's important when reloading namespaces to remove mount points
on the old function versions.

It uses finalize, which isn't free (takes extra work to allocate and GC has to work harder), so
if you don't use namespace reloading and you want to optimize you can disable reload guard objects.

Set `-Dmemento.reloadable=false` JVM option (or change `memento.config/reload-guards?` var root binding).

## Developer information

- [**Performance**](doc/performance.md)

## Breaking changes

Version 0.9.0 introduced many breaking changes.

## License

Copyright © 2020-2021 Rok Lenarčič

Licensed under the term of the Eclipse Public License - v 2.0, see LICENSE.
