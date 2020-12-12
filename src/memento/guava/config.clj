(ns memento.guava.config
  "Guava implementation config helpers.

  Contains documented definitios of standard options of Guava cache config."
  {:author "Rok Lenarčič"})

(def removal-listener
  "Corresponds to .removalListener on CacheBuilder.

  A function of three arguments (for guava cache: (fn [key value removal-cause] nil))
  or four arguments (guava cache region: (fn [f key value removal-cause] nil)),
  that will be called whenever an entry is removed.

  The four arguments are:

  - the function being cached (cache region 4 arg fn only)
  - the key (arg-list transformed by key-fn if any)
  - the value (after ret-fn being applied)
  - com.google.common.cache.RemovalCause

  Warning: any exception thrown by listener will not be propagated to the Cache user, only logged via a Logger."
  :memento.guava/removal-listener)

(def weight<
  "A long.

  Specifies the maximum weight of entries the cache may contain. If using this option,
  you must provide `kw-weight` option for cache to calculate the weight of entries.

  A cache may evict entries before the specified limit is reached."
  :memento.guava/weight<)

(def kv-weight
  "A function of 2-arguments (for guava cache: (fn [key value] int-weight))
  or 3 arguments (guava cache region: (fn [f key value] int-weight)),
  that will be used to determine the weight of entries.

  It should return an int, the weight of the entry.

  The 3 arguments are:
  - the first argument is the function being cached (cache region 3 arg fn only)
  - the second argument is the key (arg-list transformed by key-fn if any)
  - the third argument is the value (after ret-fn being applied)"
  :memento.guava/kv-weight)

(def weak-keys
  "Corresponds to .weakKeys on CacheBuilder.

  Boolean flag, enabling storing keys using weak references.

  Specifies that each key (not value) stored in the cache should be wrapped in a WeakReference
  (by default, strong references are used).

  Warning: when this method is used, the resulting cache will use identity (==) comparison
  to determine equality of keys. Its Cache.asMap() view will therefore technically violate
  the Map specification (in the same way that IdentityHashMap does).

  The identity comparison makes this not very useful."
  :memento.guava/weak-keys)

(def weak-values
  "Corresponds to .weakValues on CacheBuilder.

  Boolean flag, enabling storing values using weak references.

  This allows entries to be garbage-collected if there are no other (strong or soft) references to the values."
  :memento.guava/weak-values)

(def soft-values
  "Corresponds to .softValues on CacheBuilder.

  Boolean flag, enabling storing values using soft references.

  Softly referenced objects are garbage-collected in a globally least-recently-used manner,
  in response to memory demand. Because of the performance implications of using soft references,
  we generally recommend using the more predictable maximum cache size instead."
  :memento.guava/soft-values)

(def stats
  "Boolean flag, enabling collection of stats.

  Corresponds to .enableStats on CacheBuilder.

  You can retrieve a cache's stats by using memento.guava/stats or
  memento.guava/region-stats.

  Returns com.google.common.cache.CacheStats instance or nil."
  :memento.guava/stats)

(def ticker
  "Corresponds to .ticker on CacheBuilder.

  A function of zero arguments that should return current nano time.
  This is used when doing time based eviction.

  The default is (fn [] (System/nanoTime)).

  This is useful for testing and you can also make the time move in discrete amounts (e.g. you can
  make all cache accesses in a request have same time w.r.t. eviction)."
  :memento.guava/ticker)
