(ns memento.core
  "Memoization library."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.caffeine]
            [memento.multi :as multi]
            [memento.mount :as mount])
  (:import (memento.base EntryMeta ICache)
           (memento.mount CachedFn IMountPoint)))

(defn do-not-cache
  "Wrap a function result value in a wrapper that tells the Cache not to
  cache this particular value."
  [v]
  (if (instance? EntryMeta v)
    (do (.setNoCache ^EntryMeta v true) v)
    (EntryMeta. v true #{})))

(defn with-tag-id
  "Wrap a function result value in a wrapper that has the given additional
  tag + ID information. You can add multiple IDs for same tag.

  This information is later used by memo-clear-tag!."
  [v tag id]
  (if (instance? EntryMeta v)
    (do (.setTagIdents ^EntryMeta v (conj (.getTagIdents ^EntryMeta v) [tag id])) v)
    (EntryMeta. v false #{[tag id]})))

(defn create
  "Create a cache.

  A conf is a map of cache settings, see memento.config namespace for names of settings."
  [conf]
  (base/base-create-cache conf))

(defn bind
  "Bind the cache to a function or a var. If a var is specified, then var root
   binding is modified.

   The mount-conf is a configuration options for mount point.

   It can be a map with options, a vector of tags, or one tag.

   Supported options are:
   - memento.core/key-fn
   - memento.core/key-fn*
   - memento.core/ret-fn
   - memento.core/tags
   - memento.core/seed"
  [fn-or-var mount-conf cache]
  (when-not (instance? ICache cache)
    (throw (IllegalArgumentException. "Argument should satisfy memento.base/Cache")))
  (mount/bind fn-or-var mount-conf cache))

(defn memo
  "Combines cache create and bind operations from this namespace.

  If conf is provided, it is used as mount-conf in bind operation, but with any extra map keys
  going into cache create configuration.

  If no configuration is provided, meta of the fn or var is examined.

  The value of :memento.core/cache meta key is used as conf parameter
  in memento.core/memo. If :memento.core/mount key is also present, then
  they are used as cache and conf parameters respectively."
  ([fn-or-var]
   (let [{::keys [mount cache]} (meta fn-or-var)]
     (if mount (memo fn-or-var mount cache)
               (memo fn-or-var cache))))
  ([fn-or-var conf]
   (if (map? conf)
     (memo fn-or-var
           (select-keys conf mount/configuration-props)
           (apply dissoc conf mount/configuration-props))
     (memo fn-or-var conf {})))
  ([fn-or-var mount-conf cache-conf]
   (->> cache-conf
        create
        (bind fn-or-var mount-conf))))

(defmacro defmemo
  "Like defn, but immediately wraps var in a memo call. It expects caching configuration
  to be in meta under memento.core/cache key, as expected by memo."
  {:arglists '([name doc-string? attr-map? [params*] prepost-map? body]
               [name doc-string? attr-map? ([params*] prepost-map? body)+ attr-map?])}
  [& body]
  `(memo (defn ~@body)))

(defn active-cache
  "Return Cache instance from the function, if present."
  [f] (some-> (mount/mount-point f) mount/mounted-cache))

(defn memoized?
  "Returns true if function is memoized."
  [f] (instance? CachedFn f))

(defn memo-unwrap
  "Takes a function and returns an uncached function."
  [f] (if (instance? CachedFn f) (.getOriginalFn ^CachedFn f) f))

(defn memo-clear-cache!
  "Invalidate all entries in Cache. Returns cache."
  [cache]
  (when-not (instance? ICache cache)
    (throw (IllegalArgumentException. "Argument should satisfy memento.base/Cache")))
  (base/invalidate-all cache))

(defn memo-clear!
  "Invalidate one entry (f with arglist) on memoized function f,
   or invalidate all entries for memoized function. Returns f."
  ([f]
   (when-let [^IMountPoint mp (mount/mount-point f)] (.invalidateAll mp))
   f)
  ([f & fargs]
   (when-let [^IMountPoint mp (mount/mount-point f)] (.invalidate mp fargs))
   f))

(defn memo-add!
  "Add map's entries to the cache. The keys are argument-lists.

  Returns f."
  [f m]
  (when-let [^IMountPoint mp (mount/mount-point f)] (.addEntries mp m))
  f)

(defn as-map
  "Return a map representation of the memoized entries on this function."
  [f]
  (when-let [^IMountPoint mp (mount/mount-point f)] (.asMap mp)))

(defn tags
  "Return tags of the memoized function."
  [f]
  (when-let [^IMountPoint mp (mount/mount-point f)] (.getTags mp)))

(defn mounts-by-tag
  "Returns a sequence of MountPoint instances used by memoized functions which are tagged by this tag."
  [tag]
  (get @mount/tags tag []))

(defn fire-event!
  "Fire an event payload to the single cached function or all tagged functions, if tag
  is provided."
  [f-or-tag evt]
  (if (instance? IMountPoint f-or-tag)
    (.handleEvent ^IMountPoint f-or-tag evt)
    (->> (mounts-by-tag f-or-tag)
         (eduction (map #(.handleEvent ^IMountPoint % evt)))
         dorun)))

(defn memo-clear-tag!
  "Invalidate all entries that have the specified tag + id metadata. ID can be anything."
  [tag id]
  (let [secondary-key [tag id]]
    (->> (mounts-by-tag tag)
         (eduction (map mount/mounted-cache)
                   (distinct)
                   (map #(base/invalidate-id % secondary-key)))
         dorun)))

(defn update-tag-caches!
  "For each memoized function with the specified tag, set the Cache used by the fn to (cache-fn current-cache).

  Cache update function is ran on each
  memoized function (mount point), so if one cache is backing multiple functions, the cache update function is called
  multiple timed on it. If you want to run cache-fn one each Cache instance only once, I recommend wrapping it
  in clojure.core/memoize.

  If caches are thread-bound to a different value with with-caches, then those
  bindings are modified instead of root bindings."
  [tag cache-fn]
  (mount/alter-caches-mapping tag mount/update-existing cache-fn))

(defmacro with-caches
  "Within the block, each memoized function with the specified tag has its cache update by cache-fn.

  The values are bound within the block as a thread local binding. Cache update function is ran on each
  memoized function (mount point), so if one cache is backing multiple functions, the cache update function is called
  multiple timed on it. If you want to run cache-fn one each Cache instance only once, I recommend wrapping it
  in clojure.core/memoize."
  [tag cache-fn & body]
  `(binding [mount/*caches* (mount/update-existing mount/*caches* (get @mount/tags ~tag []) ~cache-fn)]
     ~@body))

(defn evt-cache-add
  "Convenience function. It creates or wraps event handler fn,
  with an implementation which expects an event to be a vector of
  [event-type payload], it checks for matching event type and inserts
  the result of (->entries payload) into the cache."
  ([evt-type ->entries] (evt-cache-add (constantly nil) evt-type ->entries))
  ([evt-fn evt-type ->entries]
   (fn [mountp evt]
     (when (and (vector? evt)
                (= (count evt) 2)
                (= evt-type (first evt)))
       (memo-add! mountp (->entries (second evt))))
     (evt-fn mountp evt))))

(defn tiered
  "Creates a configuration for a tiered cache. Both parameters are either a conf map or a cache.

  Entry is fetched from cache, delegating to upstream is not found. After the operation
  the entry is in both caches.

  Useful when upstream is a big cache that outside the JVM, but it's not that inexpensive, so you
  want a local smaller cache in front of it.

  Invalidation operations also affect upstream. Other operations only affect local cache."
  [cache upstream]
  {::type ::tiered
   ::multi/cache cache
   ::multi/upstream upstream})

(defn consulting
  "Creates a configuration for a consulting tiered cache. Both parameters are either a conf map or a cache.

  Entry is fetched from cache, if not found, the upstream is asked for entry if present (but not to make one
  in the upstream).

  After the operation, the entry is in local cache, upstream is unchanged.

  Useful when you want to consult a long term upstream cache for existing entries, but you don't want any
  entries being created for the short term cache to be pushed upstream.

  Invalidation operations also affect upstream. Other operations only affect local cache."
  [cache upstream]
  {::type ::consulting
   ::multi/cache cache
   ::multi/upstream upstream})

(defn daisy
  "Creates a configuration for a daisy chained cache. Cache parameter is a conf map or a cache.

  Entry is returned from cache IF PRESENT, otherwise upstream is hit. The returned value
  is NOT added to cache.

  After the operation the entry is either in local or upstream cache.

  Useful when you don't want entries from upstream accumulating in local
  cache, and you're feeding the local cache via some other means:
  - a preloaded fixed cache
  - manually adding entries

  Invalidation operations also affect upstream. Other operations only affect local cache."
  [cache upstream]
  {::type ::daisy
   ::multi/cache cache
   ::multi/upstream upstream})

(defmacro if-cached
  "Like if-let, but then clause is executed if the call in the binding is cached, with the binding symbol
  being bound to the cached value.

  This assumes that the top form in bindings is a call of cached function, generating an error otherwise.

  e.g. (if-cached [my-val (my-cached-fn arg1)] ...)"
  ([bindings then]
   `(if-cached ~bindings ~then nil))
  ([bindings then else]
   (assert (vector? bindings))
   (assert (= 2 (count bindings)))
   (let [form (bindings 0)
         cache-call (bindings 1)
         _ (assert (list? cache-call))
         f (first cache-call)
         _ (assert (symbol? f))]
     `(if-let [mnt# (mount/mount-point ~(first cache-call))]
        (let [mnt# (if (instance? CachedFn mnt#) (.getMp mnt#) mnt#)
              cached# (.ifCached mnt# '~(next cache-call))]
          (if (= cached# base/absent)
            ~else
            (let [~form cached#] ~then)))
        (throw (ex-info (str "Function " ~(str f) " is not a cached function")
                        {:form '~cache-call}))))))
