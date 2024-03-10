(ns memento.caffeine.config
  "Caffeine implementation config helpers.

  Contains documented definitions of standard options of Caffeine cache config."
  {:author "Rok Lenarčič"})

(def removal-listener
  "Cache setting, corresponds to .removalListener on Caffeine.

  A function of four arguments (fn [f key value removal-cause] nil),
  that will be called whenever an entry is removed.

  The four arguments are:

  - the function being cached
  - the key (arg-list transformed by key-fn if any)
  - the value (after ret-fn being applied)
  - com.github.benmanes.caffeine.cache.RemovalCause

  Warning: any exception thrown by listener will not be propagated to the Cache user, only logged via a Logger."
  :memento.caffeine/removal-listener)

(def weight<
  "Cache setting, a long.

  Specifies the maximum weight of entries the cache may contain. If using this option,
  you must provide `kw-weight` option for cache to calculate the weight of entries.

  A cache may evict entries before the specified limit is reached."
  :memento.caffeine/weight<)

(def kv-weight
  "Cache setting, a function of 3 arguments (fn [f key value] int-weight),
  that will be used to determine the weight of entries.

  It should return an int, the weight of the entry.

  The 3 arguments are:
  - the first argument is the function being cached
  - the second argument is the key (arg-list transformed by key-fn if any)
  - the third argument is the value (after ret-fn being applied)"
  :memento.caffeine/kv-weight)

(def weak-keys
  "Cache setting, corresponds to .weakKeys on CacheBuilder.

  Boolean flag, enabling storing keys using weak references.

  Specifies that each key (not value) stored in the cache should be wrapped in a WeakReference
  (by default, strong references are used).

  Warning: when this method is used, the resulting cache will use identity (==) comparison
  to determine equality of keys. Its Cache.asMap() view will therefore technically violate
  the Map specification (in the same way that IdentityHashMap does).

  The identity comparison makes this not very useful."
  :memento.caffeine/weak-keys)

;; these don't work with our values, we will have to do the work ourselves (future version)
#_(def weak-values
  "Cache setting, corresponds to .weakValues on CacheBuilder.

  Boolean flag, enabling storing values using weak references.

  This allows entries to be garbage-collected if there are no other (strong or soft) references to the values."
  :memento.caffeine/weak-values)

(def soft-values
  "Cache setting, corresponds to .softValues on CacheBuilder.

  Boolean flag, enabling storing values using soft references.

  Softly referenced objects are garbage-collected in a globally least-recently-used manner,
  in response to memory demand. Because of the performance implications of using soft references,
  we generally recommend using the more predictable maximum cache size instead.

  In Memento's case, SoftReference points the CompletableFuture containing the value, so it's always
  softly reachable."
  :memento.caffeine/soft-values)

(def stats
  "Cache setting, boolean flag, enabling collection of stats.

  Corresponds to .enableStats on CacheBuilder.

  You can retrieve a cache's stats by using memento.caffeine/stats.

  Returns com.google.common.cache.CacheStats instance or nil."
  :memento.caffeine/stats)

(def ticker
  "Cache setting, corresponds to .ticker on CacheBuilder.

  A function of zero arguments that should return current nano time.
  This is used when doing time based eviction.

  The default is (fn [] (System/nanoTime)).

  This is useful for testing and you can also make the time move in discrete amounts (e.g. you can
  make all cache accesses in a request have same time w.r.t. eviction)."
  :memento.caffeine/ticker)
