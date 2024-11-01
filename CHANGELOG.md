# Changelog

## 2.0.68

- correctly wraps MultiFn

## 2.0.65

- added a check that will throw an error if encountering mc/cache key in wrong place, cache configuration

## 2.0.63

- upgrade from Caffeine 2 to Caffeine 3. Min Java changed from 8 to 11.

## 1.4.62

- remove unneeded operation when adding entries into the map

## 1.4.61

- when an tag is invalidated during load, the loading thread will be interrupted

## 1.3.60

- improved variable expiry
- added stronger prevention of the use of invalid entries

## 1.2.59

- added variable expiry option (see README)
- removed some reflection
- enabled weakValues as an option for caffeine cache
- removed weakKeys as it's not possible to use that option

## 1.2.58

- added ret-ex-fn option to transform exceptions being thrown by cache in the same way ret-fn works for values

## 1.2.57

- add predicate memento.core/none-cache? that checks if cache is of none type
- new function memento.core/caches-by-tag
- important improvement of atomicity for invalidations by tag or function
- important fix for thread synchronization when adding tagged entries
- important fix for secondary indexes clearing
- reduced memory use
- improving performance on evictions when an eviction listener isn't used
- *BREAKING CHANGE FOR IMPLEMENTATIONS* `invalidateId` is now `invalidateIds` and takes an iterable of tag ids, the implementations are expected to take care to block loads until invalidations are complete. Use the `memento.base/lockout-map` for this purpose.

## 1.1.54

- Improve handling of Vectors when adding entries

## 1.1.53

- improve memory use

## 1.1.52

- fixes a bug with concurrent loads causing some of them to return nil as a result

## 1.1.51

- DO NOT USE
- add check for cyclical loads to caffeine cache, e.g. cached function calling itself with same parameters, this now throws StackOverflowError, which is the error you'd get in this situation with uncached function
- improved performance

## 1.1.50

- added option of using SoftReferences in caffeine cache
- fixed reload-guards? var not being redefinable

## 1.1.45

- add getters/setters to MultiCache for the delegate/upstream cache, also add clojure functions to access these properties to `memento.multi` namespace
- moved CacheKey to memento.base Java package

## 1.1.44

- fix bug which would have the cache return nil when concurrently accessing a value being calculated that ends being uncacheable

## 1.1.42
- big internal changes now uses Java objects for most things for smaller memory profile and smaller callstack
- significant improvements to callstack size for cached call
- **This is breaking changes for any implementation, shouldn't affect users**
- Fixes issue where namespace scan would stack caches on the same function over and over if called multiple times

Here is an example of previous callstack of recursively cached call (without ret-fn):
```clojure
	at myns$myfn.invoke(myns.clj:12)
	at clojure.lang.AFn.applyToHelper(AFn.java:160)
	at clojure.lang.AFn.applyTo(AFn.java:144)
	at clojure.core$apply.invokeStatic(core.clj:667)
	at clojure.core$apply.invoke(core.clj:662)
	at memento.caffeine.CaffeineCache$fn__2536.invoke(caffeine.clj:122)
	at memento.caffeine.CaffeineCache.cached(caffeine.clj:121)
	at memento.mount.UntaggedMountPoint.cached(mount.clj:50)
	at memento.mount$bind$fn__2432.doInvoke(mount.clj:119)
	at clojure.lang.RestFn.applyTo(RestFn.java:137)
	at clojure.lang.AFunction$1.doInvoke(AFunction.java:31)
	at clojure.lang.RestFn.invoke(RestFn.java:436)
	at myns$myfn.invokeStatic(myns.clj:17)
	at myns$myfn.invoke(myns.clj:12)
```

And callstack after:
```clojure
	at myns$myfn.invoke(myns.clj:12)
	at clojure.lang.AFn.applyToHelper(AFn.java:160)
	at memento.caffeine.CaffeineCache$fn__2052.invoke(caffeine.clj:120)
	at memento.caffeine.CaffeineCache.cached(caffeine.clj:119)
	at memento.mount.CachedFn.invoke(CachedFn.java:110)
	at myns$myfn.invokeStatic(myns.clj:17)
	at myns$myfn.invoke(myns.clj:12)
```

From 11 stack frames to 4.

## 1.0.37
- remove Guava and replace with Caffeine
- rewrite the readme
- mark Guava namespaces for deprecation

## 0.9.36 (2022-03-01)
- add `memento.core/defmemo`

## 0.9.35 (2022-02-22)
- add `memento.core/key-fn*` mount setting

## 0.9.34 (2022-02-16)
- add support for using meta 

## 0.9.3 (2021-05-08)
### Features added
- `memento.core/if-config`
