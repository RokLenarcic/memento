(ns memento.base
  "Memoization library with many features.

  This namespace includes internal tooling and shouldn't be used directly
  unless writing extensions."
  {:author "Rok Lenarčič"})

; use alter-var-root to update this, since config calls will generally happen during namespace loading
(def ^:dynamic regions {})

(defrecord NonCached [v])

(defn unwrap-noncached [o]
  (loop [v o]
    (if (instance? NonCached v) (recur (:v v)) v)))

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
  [obj] (::cache (meta obj)))

(defprotocol Cache
  "Protocol for Cache. If Cache for a Region, it needs to check if Region is started
  to engage in caching."
  (get-cached [this args]
    "Return cached value, possibly invoking the function with the args to
    obtain the value.

    This should be a thread-safe atomic operation.")
  (invalidate [this arg] "Invalidate entry for args, returns Cache")
  (invalidate-all [this] "Invalidate all entries, returns Cache")
  (put-all [this args-to-vals] "Add entries to cache, returns Cache")
  (as-map [this] "Returns the cache as a map. This does not imply a snapshot,
  as implementation might provide a weakly consistent view of the cache."))

(defprotocol CacheRegion
  "Protocol for Caches for the regions. Main difference from Cache being
  that is houses entries for multiple functions. As such it takes additional
  parameter of function."
  (get-cached* [this region-cache args]
    "Return the cache value for the function and args, possibly invoking the function.
    - region-cache is RegionCache instance
    This should be a thread-safe atomic operation.")
  (invalidate* [this region-cache args] "Invalidate an arglist for RegionCache")
  (invalidate-cached [this region-cache] "Invalidate all keys stemming from this RegionCache")
  (invalidate-region [this] "Invalidate all entries, returns Region")
  (put-all* [this region-cache args-to-vals] "Add entries to RegionCache")
  (cache-as-map [this region-cache] "Return all entries for a RegionCache.")
  (as-map* [this] "Returns the cache as a map. This does not imply a snapshot,
  as implementation might provide a weakly consistent view of the cache."))

(defrecord RegionCache
  [region-id key-fn ret-fn f]
  Cache
  (get-cached [this args]
    (if-let [region (regions region-id)]
      (get-cached* region this args)
      (unwrap-noncached (ret-fn (apply f args)))))
  (invalidate [this args]
    (when-some [region (regions region-id)]
      (invalidate* region this args))
    this)
  (invalidate-all [this]
    (when-some [region (regions region-id)]
      (invalidate-cached region this))
    this)
  (put-all [this args-to-vals]
    (when-some [region (regions region-id)]
      (put-all* region this args-to-vals))
    this)
  (as-map [this]
    (if-some [region (regions region-id)]
      (cache-as-map region this)
      {})))

(defmulti create-cache
          "Creates a cache for a function with the spec."
          (fn [spec f] (:memento.core/type spec)))

(defmethod create-cache :memento.core/regional
  [{:memento.core/keys [seed region key-fn ret-fn]} f]
  (cond->
    (->RegionCache region (or key-fn identity) (or ret-fn identity) f)
    (map? seed) (put-all seed)))

(defmulti create-region (fn [spec] (:memento.core/type spec)))

(defn new-region
  "Convenience function for making new regions"
  [region-id raw-spec default-type]
  (create-region (merge
                   #:memento.core
                       {:region region-id
                        :type default-type
                        :key-fn identity
                        :ret-fn identity}
                   (direct-spec raw-spec))))

(defn attach
  "Attach a cache to a fn or var. Internal function.

  Scrape var or fn meta and add it to the spec.

  Merge fn meta, var meta, spec."
  [f spec enabled? default-type]
  (if (var? f)
    (alter-var-root f attach (merge (meta f) spec) enabled? default-type)
    (let [final-spec (merge {:memento.core/type default-type} (meta f) spec)
          cache (create-cache final-spec f)]
      (with-meta
        (if enabled? (fn [& args] (get-cached cache args))
                     (fn [& args] (unwrap-noncached (apply f args))))
        {::cache cache
         ::original f}))))
