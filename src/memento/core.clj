(ns memento.core
  "Memoization library with many features."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b]
            [memento.guava]))

(def enabled? (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def ^:dynamic *default-type* ::guava)

(defn set-region!
  "Create a new Cache Region and bind it to region-id.

  It will overwrite existing region (generally dropping that cache)."
  [region-id spec]
  (alter-var-root #'b/regions #(assoc % region-id (b/new-region region-id spec *default-type*))))

(defn non-cached
  "Create a cache result that will not be cached."
  [v] (b/->NonCached v))

(defn memo
  "Attach a cache to a function or a var. If var is specified, then var root
  binding is modified with the cached version of the value.

  If spec is a map, then we merge:
  - fn meta
  - var meta (if applicable)
  - the map given (with all non-namespaced keys getting memento.core namespace)

  This is used to create the cache.

  If :memento.core/region is set the a regional cache is created.

  If spec is not a map, it's equivalent to {:memento.core/region spec}"
  ([fn-or-var] (memo {} fn-or-var))
  ([spec fn-or-var]
   (if (map? spec)
     (b/attach fn-or-var
               (merge (b/direct-spec spec)
                      (when (::region spec)
                        {::type ::regional}))
               enabled?
               *default-type*)
     (memo {::region spec} fn-or-var))))

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
  ([f & fargs]
   (some-> (b/active-cache f)
           (b/invalidate fargs))))

(defn memo-clear-region!
  "Invalidate entries in a cache region.

  If only region-id is supplied the whole cache region is cleared,
  otherwise the specified entry (function and args).

  The function used here needs to version."
  [region-id]
  (some-> (get b/regions region-id)
          (b/invalidate-region)))

(defn memo-add!
  "Add map's entries to the cache. The keys are argument-lists.

  Returns Cache, if one was present on the function."
  [f m]
  (some-> (b/active-cache f)
          (b/put-all m)))

(defn as-map
  "Return a map representation of the memoized entries on this function."
  [f]
  (some-> (b/active-cache f) b/as-map))

(defn region-as-map
  "Return a map of memoized entries of the Cache Region."
  [region-id]
  (some-> (get b/regions region-id) b/as-map*))

(defmacro with-region
  "Run forms with region-id bound to a new region (thread-local)."
  [region-id reg-desc & forms]
  `(binding [b/regions (assoc b/regions ~region-id (b/new-region ~region-id ~reg-desc *default-type*))]
     ~@forms))

(defmacro with-region-alias
  "Run forms with region-alias aliasing the existing region region-id (thread-local)."
  [region-alias region-id & forms]
  `(binding [b/regions (assoc b/regions ~region-alias (get b/regions ~region-id))]
     ~@forms))
