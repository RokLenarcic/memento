(ns memento.core
  "Memoization library with many features."
  {:author "Rok Lenarčič"}
  (:require [memento.impl :as impl]
            [memento.base :as base]
            [memento.guava]))

(defn do-not-cache
  "Create a cache result that will not be cached."
  [v] (base/->DoNotCache v))

(defn memo
  "Attach a cache to a function or a var. If var is specified, then var root
  binding is modified with the cached version of the value.


  If conf is a map, then we merge:
  - fn meta
  - var meta (if applicable)
  - the map given (with all non-namespaced keys getting memento.core namespace)

  This is used to create the cache.

  If :memento.core/region is set the a regional cache is created.

  If conf is not a map, it's equivalent to {:memento.core/region conf}"
  [conf fn-or-var]
  (if (map? conf)
    (impl/attach fn-or-var
                 (merge conf
                        (when (::region conf)
                          {::type ::regional})))
    (memo {::region conf} fn-or-var)))

(defn memoized?
  "Returns true if function is memoized."
  [f]
  (boolean (impl/active-cache f)))

(defn memo-unwrap
  "Takes a function and returns uncached function."
  [f] (if-let [c (impl/active-cache f)] (base/original-function c) f))

(defn memo-clear!
  "Invalidate one or all entries on the memoized function.

  Returns Cache, if one was present on the function."
  ([f]
   (some-> (impl/active-cache f)
           base/invalidate-all))
  ([f & fargs]
   (some-> (impl/active-cache f)
           (base/invalidate fargs))))

(defn memo-add!
  "Add map's entries to the cache. The keys are argument-lists.

  Returns Cache, if one was present on the function."
  [f m]
  (some-> (impl/active-cache f)
          (base/put-all m)))

(defn as-map
  "Return a map representation of the memoized entries on this function."
  [f]
  (some-> (impl/active-cache f) base/as-map))

#_(defn update-tagged-cache
  "Updates all caches with the tag with the provided cache-fn. It receiv"
  [f cache-fn tag]
  )

#_(defn process-tagged-cache
  )
