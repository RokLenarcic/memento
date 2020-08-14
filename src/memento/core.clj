(ns memento.core
  "Memoization library with many features."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b]
            [memento.guava]
            [meta-merge.core :refer [meta-merge]])
  (:import (memento.base NonCached)))

(def enabled? (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def ^:dynamic *defaults*
  "Default specs for a cache type and default cache type."
  {:default-type :guava
   :type-defaults {:guava {}}})

(defn non-cached
  "Create a cache result that will not be cached."
  [v] (b/->NonCached v))

(defn -merge-region-specs
  "Internal function.

  Performs merging logic for region definitions."
  [region spec]
  (let [typ (::type spec (:default-type *defaults*))]
    (assoc (meta-merge (get-in *defaults* [:type-defaults typ])
                       spec)
      ::typ typ
      ::region region)))

(defn -merge-cache-specs
  "Internal function.

  Performs merging logic for cache specs, it also makes sure that the
  region settings are correctly applied.

  If a region is specified in the specs then:
  - region's spec's are meta-merged with given specs and used to create a cache.

  Otherwise:
  Type is selected from specs, then used to select base properties from *defaults*,
  which are meta-merged with specs."
  [spec]
  (let [region (get b/*regions* (::region spec))
        typ (::type (if region (b/region-spec region) spec) (:default-type *defaults*))]
    (if region
      (assoc (meta-merge (b/region-spec region) spec)
        ::type typ
        ::region (b/region-id region))
      (assoc (meta-merge (get-in *defaults* [:type-defaults typ])
                         spec)
        ::typ typ))))

(defn -attach
  "Attach a cache to a fn or var. Internal function.

  Scrape var or fn meta and add it to the spec."
  [f spec]
  (if (var? f)
    (alter-var-root f -attach (meta-merge (meta f) spec))
    (let [cache (b/create-cache (-merge-cache-specs (meta-merge (meta f) spec)))]
      (with-meta
        (if enabled? (fn [& args] (b/get-cached cache args))
                     (fn [& args]
                       (loop [v (apply f args)]
                         (if (instance? NonCached v) (recur (:v v)) v))))
        {::cache cache
         ::original f}))))

(defn memo
  ([fn-or-var] (memo {} fn-or-var))
  ([spec fn-or-var]
   (if (map? spec)
     (-attach fn-or-var (b/direct-spec spec))
     (memo {::region spec} fn-or-var))))

(defn memoized?
  "Returns true if function is memoized."
  [f]
  (boolean (b/active-cache f)))

(defn memo-unwrap
  "Takes a function and returns uncached function."
  [f] (or (-> f meta ::original) f))

(defn memo-clear!
  "Invalidate one or all entries on the memoized function.

  Returns Cache, if one was present on the function."
  ([f]
   (some-> (b/active-cache f)
           b/invalidate-all))
  ([f fargs]
   (some-> (b/active-cache f)
           (b/invalidate fargs))))

(defn memo-clear-region!
  "Invalidate all entries in a region.

  Returns the region."
  [region-id]
  (some-> (b/region-cache region-id)
          b/invalidate-region))

(defn memo-clear-regions!!
  "Invalidate all regions."
  []
  (doseq [region (vals b/*regions*)]
    (b/invalidate-region region)))

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

(defmacro with-updated-region
  "Withing statement scope the region is replaced with a new non-started region,
  with the new spec (based on update-spec-fn)"
  [region spec-update-fn & body]
  `(binding [b/*regions* (update b/*regions* ~region
                                 (comp b/create-region
                                       (partial -merge-region-specs ~region)
                                       ~spec-update-fn
                                       b/region-spec))]
     ~@body))

(defmacro with-started-region
  "Within statement scope the region is started, if not started already.
 "
  [region & body]
  `(binding [b/*regions* (update b/*regions* ~region b/started-region)]
     ~@body))

(defn create-region!
  "Register a cache region.

  If start? is true also starts the region, defaults to true."
  ([region spec]
   (create-region! region spec true))
  ([region spec start?]
   (alter-var-root #'b/*regions*
                   (fn [regions]
                     (let [final-spec (-merge-region-specs region (b/direct-spec spec))]
                       (assoc regions
                         region
                         (cond-> (b/create-region final-spec)
                           start? (b/started-region))))))))
