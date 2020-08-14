(ns memento.base
  "Memoization library with many features.

  This namespace includes internal tooling and shouldn't be used directly
  unless writing extensions."
  {:author "Rok Lenarčič"})

(def ^:dynamic *regions*
  "Storage for the region caches." {})

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
  (reduce-kv (fn [m k v] (assoc m (add-keyword-ns "memento.core" k) v)) {} spec))

(defn active-cache
  "Return active cache from the object's meta."
  [obj]
  (:memento.core/cache (meta obj)))

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
  as implementation might provide a weakly consistent view of the cache."))

(defn region-cache [region] (get-in *regions* [region :memento.core/cache]))

(defprotocol CacheRegion
  "Cache region. Caches created from this region use same pool of resources,
  such as max-size, ttl-queue etc. Removing a region thus also removes multiple
  function's caches at once.

  Region should not be created with a backend cache running and should wait for start-region call."
  (started-region [this] "If region is not started, returns a new started CacheRegion, otherwise returns this.")
  (invalidate-region [this] "Remove all entries in the region.")
  (region-id [this] "Return region ID to be used for this region")
  (region-spec [this] "Return spec of the region."))

(defmulti create-cache
          "Creates a cache for a function with the spec."
          (fn [spec f] (:memento.core/type spec)))

(defmulti create-region
          "Create a cache region from a spec map (containing things such as eviction settings,
          ttl times, etc....).

          All caches created in the region share these properties, i.e. in a region that houses
          3 function caches, if max size is set to 100, that is the limit for the sum of cached
          entries in all 3 caches.

          This is usually achieved by a single backing data-structure.

          Contains at least keys:
          - memento.core/type (cache type)
          - memento.core/region (region id)"
          (fn [spec] (:memento.core/type spec)))
