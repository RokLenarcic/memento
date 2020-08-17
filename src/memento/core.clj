(ns memento.core
  "Memoization library with many features."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b]
            [memento.guava]
            [meta-merge.core :refer [meta-merge]]))

(def enabled? (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def ^:dynamic *default-type* ::guava)
(derive :memento.core/scoped :memento.core/cache)

(defn configure-cache!
  "Configure the cache type. This is specific for each basic cache type.

  If type is not known it will create a new ::guava-region subtype.

  Built-ins:

  - memoize.core/guava: configuration will be added as default spec for
  caches of this type
  - memoize.core/guava-region: spec is applied to create the region and
  its cache, recreating the region and any derived regions.
  - memoize.core/scoped: configuration will be added to the next"
  [typ spec]
  (b/configure-cache typ (with-meta (b/direct-spec spec) (meta spec))))

(defn non-cached
  "Create a cache result that will not be cached."
  [v] (b/->NonCached v))

(defn memo
  ([fn-or-var] (memo {} fn-or-var))
  ([spec fn-or-var]
   (if (map? spec)
     (b/attach enabled? fn-or-var (b/direct-spec spec))
     (memo {::type (b/add-keyword-ns "memento.core" spec)} fn-or-var))))

(defn memoized?
  "Returns true if function is memoized."
  [f]
  (boolean (b/active-cache f)))

(defn memo-unwrap
  "Takes a function and returns uncached function."
  [f] (or (-> f meta ::b/original) f))

(defn memo-clear!
  "Invalidate one or all entries on the memoized function.

  Returns Cache, if one was present on the function."
  ([f]
   (some-> (b/active-cache f)
           b/invalidate-all))
  ([f fargs]
   (some-> (b/active-cache f)
           (b/invalidate fargs))))

(defn memo-nuke!
  "Invalidate all entries in a cache region. This depends on implementation..

  Returns the cache."
  [f]
  (some-> (b/active-cache f)
          b/nuke!))

(defn memo-add!
  "Add map's entries to the cache. The keys are argument-lists.

  Returns Cache, if one was present on the function."
  [f m]
  (some-> (b/active-cache f)
          (b/put-all m)))

(defn as-map
  "Return a map representation of the memoized entries on this function.

  If using a cache region, return all entries in the region, where
  first element of cache key is
  If return all entries in the region,
  not just those "
  [f]
  (some-> (b/active-cache f) b/as-map))

(defn configure-guava-cache!
  "Configures a guava cache subtype. This is a convenience wrapper around `configure-cache`.

  Region is a keyword, if you don't provide the namespace, the namespace is
  memento.core."
  ([subtype spec]
   (derive (b/add-keyword-ns "memento.core" subtype) ::guava)
   (configure-cache! (b/add-keyword-ns "memento.core" subtype) spec)))

(defn configure-guava-region!
  "Configures a guava region cache. This is a convenience wrapper around `configure-cache`.

  Region is a keyword, if you don't provide the namespace, the namespace is
  memento.core."
  ([region spec]
   (derive (b/add-keyword-ns "memento.core" region) ::guava-region)
   (configure-cache! (b/add-keyword-ns "memento.core" region) spec)))

#_(defmacro with-scope
  "Create a new cache that is active within this scope."
  [typ spec & body]
  (let [cache (b/enter-scope (b/add-keyword-ns "memento.core" typ) (or spec {}))]
    (try
      ~@body
      (finally (b/exit-scope (b/add-keyword-ns "memento.core" typ))))))
