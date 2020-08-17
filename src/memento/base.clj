(ns memento.base
  "Memoization library with many features.

  This namespace includes internal tooling and shouldn't be used directly
  unless writing extensions."
  {:author "Rok Lenarčič"}
  (:require [meta-merge.core :refer [meta-merge]]))

; use alter-var-root to update this, since config calls will generally happen during namespace loading
(def configs {})

(defn grab-and-merge-configs
  "Convenience function for merging"
  [typ spec]
  (->> (tree-seq some? parents typ)
       (keep configs)
       (cons spec)
       reverse
       (apply meta-merge)))

(defrecord NonCached [v])

(defn add-keyword-ns
  [the-ns k]
  "Given a namespace string, it will augment any non-namespaced keyword with that
  namespace"
  (if (keyword? k) (keyword (or (namespace k) the-ns) (name k)) k))

(defn direct-spec
  "The spec given directly (not from var or fn meta) can have non-namespaced keys, which
  we will namespace."
  [spec]
  (with-meta
    (reduce-kv (fn [m k v] (assoc m (add-keyword-ns "memento.core" k) v)) {} spec)
    (meta spec)))

(defn active-cache
  "Return active cache from the object's meta."
  [obj]
  (::cache (meta obj)))

(defprotocol Cache
  "Protocol for Cache. If Cache for a Region, it needs to check if Region is started
  to engage in caching."
  (get-cached [this args]
    "Return cached value, possibly invoking the function with the args to
    obtain the value.

    This should be a thread-safe atomic operation.")
  (invalidate [this arg] "Invalidate entry for args, returns Cache")
  (invalidate-all [this] "Invalidate all entries, returns Cache")
  (put-all [this m] "Add entries to cache, returns Cache")
  (as-map [this] "Returns the cache as a map. This does not imply a snapshot,
  as implementation might provide a weakly consistent view of the cache.")
  (nuke! [this] "Invalidate all entries in the context of this type. Can be
  implementation specific, usually this means clearing the cache region."))

#_(defrecord ScopedCache []
  Cache
  (nuke! [this] (nuke! @later-cache))
  (get-cached [this args] (get-cached @later-cache args))
  (invalidate [this args] (invalidate @later-cache args))
  (invalidate-all [this] (invalidate-all @later-cache))
  (put-all [this m] (put-all @later-cache m))
  (as-map [this] (as-map @later-cache)))

(defmulti create-cache
          "Creates a cache for a function with the spec."
          (fn [typ spec f] (:memento.core/type spec)))

(defmulti configure-cache "Configure cache type." (fn [typ spec] typ))

(defmethod configure-cache :default
  [typ spec]
  (derive typ :memoize.core/guava-region)
  (configure-cache typ spec))

(defmethod configure-cache :memento.core/scoped
  [typ spec]
  (alter-var-root
    #'configs
    (fn [configs]
      (assoc configs
        typ
        (meta-merge spec ^:displace {:memento.core/scope (ThreadLocal.)})))))

(defmethod create-cache :default
  [typ spec f]
  ; a type will be registered at a later date
  (if (::lazy-create (meta spec))
    (throw (ex-info (str "Attempted to use cache type " typ " before it was registered")
                    (assoc spec :type typ)))
    (let [later-cache (delay (create-cache typ (vary-meta spec assoc ::lazy-create true) f))]
      (reify Cache
        (nuke! [this] (nuke! @later-cache))
        (get-cached [this args] (get-cached @later-cache args))
        (invalidate [this args] (invalidate @later-cache args))
        (invalidate-all [this] (invalidate-all @later-cache))
        (put-all [this m] (put-all @later-cache m))
        (as-map [this] (as-map @later-cache))))))

(defmethod create-cache :memento.core/scoped
  [typ spec f]
  (let [cache ()]
    (reify Cache
      (nuke! [this] (nuke! @later-cache))
      (get-cached [this args] (get-cached @later-cache args))
      (invalidate [this args] (invalidate @later-cache args))
      (invalidate-all [this] (invalidate-all @later-cache))
      (put-all [this m] (put-all @later-cache m))
      (as-map [this] (as-map @later-cache)))))

(defn attach
  "Attach a cache to a fn or var. Internal function.

  Scrape var or fn meta and add it to the spec."
  [enabled? f spec]
  (if (var? f)
    (alter-var-root f attach (meta-merge (meta f) spec))
    (let [final-spec (meta-merge (meta f) spec)
          cache (create-cache (add-keyword-ns "memento.core" (:memento.core/type final-spec)) final-spec f)]
      (with-meta
        (if enabled? (fn [& args] (get-cached cache args))
                     (fn [& args]
                       (loop [v (apply f args)]
                         (if (instance? NonCached v) (recur (:v v)) v))))
        {::cache cache
         ::original f}))))
