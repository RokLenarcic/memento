(ns memento.guava.config
  "Guava implementation config helpers.

  Contains documented definitions of standard options of Guava cache config."
  {:author "Rok Lenarčič"})

(def ^:deprecated removal-listener
  "Cache setting, corresponds to .removalListener on CacheBuilder.

  A function of four arguments (fn [f key value removal-cause] nil),
  that will be called whenever an entry is removed.

  The four arguments are:

  - the function being cached
  - the key (arg-list transformed by key-fn if any)
  - the value (after ret-fn being applied)
  - com.google.common.cache.RemovalCause

  Warning: any exception thrown by listener will not be propagated to the Cache user, only logged via a Logger."
  (identity :memento.caffeine/removal-listener))

(def ^:deprecated weight<
  "Cache setting, a long.

  Specifies the maximum weight of entries the cache may contain. If using this option,
  you must provide `kw-weight` option for cache to calculate the weight of entries.

  A cache may evict entries before the specified limit is reached."
  (identity :memento.caffeine/weight<))

(def ^:deprecated kv-weight
  "Cache setting, a function of 3 arguments (fn [f key value] int-weight),
  that will be used to determine the weight of entries.

  It should return an int, the weight of the entry.

  The 3 arguments are:
  - the first argument is the function being cached
  - the second argument is the key (arg-list transformed by key-fn if any)
  - the third argument is the value (after ret-fn being applied)"
  (identity :memento.caffeine/kv-weight))

(def ^:deprecated weak-keys
  "Cache setting, corresponds to .weakKeys on CacheBuilder.

  Boolean flag, enabling storing keys using weak references.

  Specifies that each key (not value) stored in the cache should be wrapped in a WeakReference
  (by default, strong references are used).

  Warning: when this method is used, the resulting cache will use identity (==) comparison
  to determine equality of keys. Its Cache.asMap() view will therefore technically violate
  the Map specification (in the same way that IdentityHashMap does).

  The identity comparison makes this not very useful."
  (identity :memento.caffeine/weak-keys))

(def ^:deprecated weak-values
  "Cache setting, corresponds to .weakValues on CacheBuilder.

  Boolean flag, enabling storing values using weak references.

  This allows entries to be garbage-collected if there are no other (strong or soft) references to the values."
  (identity :memento.caffeine/weak-values))

(def ^:deprecated soft-values
  "Cache setting, corresponds to .softValues on CacheBuilder.

  Boolean flag, enabling storing values using soft references.

  Softly referenced objects are garbage-collected in a globally least-recently-used manner,
  in response to memory demand. Because of the performance implications of using soft references,
  we generally recommend using the more predictable maximum cache size instead."
  (identity :memento.caffeine/soft-values))

(def ^:deprecated stats
  "Cache setting, boolean flag, enabling collection of stats.

  Corresponds to .enableStats on CacheBuilder.

  You can retrieve a cache's stats by using memento.caffeine/stats.

  Returns com.google.common.cache.CacheStats instance or nil."
  (identity :memento.caffeine/stats))

(def ^:deprecated ticker
  "Cache setting, corresponds to .ticker on CacheBuilder.

  A function of zero arguments that should return current nano time.
  This is used when doing time based eviction.

  The default is (fn [] (System/nanoTime)).

  This is useful for testing and you can also make the time move in discrete amounts (e.g. you can
  make all cache accesses in a request have same time w.r.t. eviction)."
  (identity :memento.caffeine/ticker))
