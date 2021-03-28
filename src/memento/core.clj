(ns memento.core
  "Memoization library."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.config :as config]
            [memento.guava]
            [memento.mount :as mount])
  (:import (memento.base EntryMeta)))

(defn do-not-cache
  "Wrap a function result value in a wrapper that tells the Cache not to
  cache this particular value."
  [v]
  (if (instance? EntryMeta v)
    (assoc v :no-cache? true)
    (base/->EntryMeta v true #{})))

(defn with-tag-id
  "Wrap a function result value in a wrapper that has the given additional
  tag + ID information. You can add multiple IDs for same tag.

  This information is later used by memo-clear-tag!."
  [v tag id]
  (if (instance? EntryMeta v)
    (update v :tag-idents conj [tag id])
    (base/->EntryMeta v false #{[tag id]})))

(defn create
  "Create a cache.

  A conf is a map of cache settings, see memento.config namespace for names of settings."
  [conf]
  (if (satisfies? base/Cache conf)
    conf
    (if config/enabled?
      (base/new-cache (merge {config/type config/*default-type*} conf))
      base/no-cache)))

(defn bind
  "Bind the cache to a function or a var. If a var is specified, then var root
   binding is modified.

   The mount-conf is a configuration options for mount point.

   It can be a map with options, a vector of tags, or one tag.

   Supported options are:
   - memento.core/key-fn
   - memento.core/ret-fn
   - memento.core/tags
   - memento.core/seed"
  [fn-or-var mount-conf cache]
  (when-not (satisfies? base/Cache cache)
    (throw (IllegalArgumentException. "Argument should satisfy memento.base/Cache")))
  (mount/bind fn-or-var mount-conf cache))

(defn memo
  "Combines cache create and bind operations from this namespace.

  If conf is provided, it is used as mount-conf in bind operation, but with any extra map keys
  going into cache create configuration."
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

(defn active-cache
  "Return Cache instance from the function, if present."
  [f] (some-> (mount/mount-point f) mount/mounted-cache))

(defn memoized?
  "Returns true if function is memoized."
  [f] (boolean (mount/mount-point f)))

(defn memo-unwrap
  "Takes a function and returns an uncached function."
  [f] (if-let [m (mount/mount-point f)] (mount/original-function m) f))

(defn memo-clear-cache!
  "Invalidate all entries in Cache. Returns cache."
  [cache]
  (when-not (satisfies? base/Cache cache)
    (throw (IllegalArgumentException. "Argument should satisfy memento.base/Cache")))
  (base/invalidate-all cache))

(defn memo-clear!
  "Invalidate one entry (f with arglist) on memoized function f,
   or invalidate all entries for memoized function. Returns f."
  ([f]
   (do (some-> (mount/mount-point f) mount/invalidate-all)
       f))
  ([f & fargs]
   (do (some-> (mount/mount-point f) (mount/invalidate fargs))
       f)))

(defn memo-add!
  "Add map's entries to the cache. The keys are argument-lists.

  Returns f."
  [f m]
  (do (some-> (mount/mount-point f) (mount/put-all m)) f))

(defn as-map
  "Return a map representation of the memoized entries on this function."
  [f]
  (some-> (mount/mount-point f) mount/as-map))

(defn tags
  "Return tags of the memoized function."
  [f]
  (some-> (mount/mount-point f) mount/get-tags))

(defn mounts-by-tag
  "Returns a sequence of MountPoint instances used by memoized functions which are tagged by this tag."
  [tag]
  (get @mount/tags tag []))

(defn fire-event!
  "Fire an event payload to the single cached function or all tagged functions, if tag
  is provided."
  [f-or-tag evt]
  (if-let [f (mount/mount-point f-or-tag)]
    (mount/handle-event f evt)
    (->> (mounts-by-tag f-or-tag)
         (eduction (map #(mount/handle-event % evt)))
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
