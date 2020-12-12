(ns memento.region
  "Namespace for cache regions and their functions."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.config :as config]
            [memento.impl :as impl]))

; use alter-var-root to update this, since config calls will generally happen during namespace loading
(def ^:dynamic *regions* {})

(defprotocol CacheRegion
  "Protocol for the regions of caching.

  Main difference from Cache; it houses entries for multiple functions.
  The client of the protocol must provide a 0 arguments loader fn and the key by which
  it will be retrieved."
  (conf [this] "Return the conf for this region.")
  (cached [this f key args]
    "Return the cache value. Key is arg list transformed to some sort of key
     and f and args will be used to load the value")
  (uncached [this f args]
    "Return the computed value without fetching from the cache, or storing in cache.")
  (if-cached [this f key]
    "Return cached value if present in cache or memento.base/absent otherwise.")
  (invalidate [this f] [this f key]
    "Invalidate all the entries linked to function or a single arg list, return Region")
  (invalidate-all [this] "Invalidate all entries, returns Region")
  (put-all [this f keys-to-vals] "Add entries as for a function")
  (as-map [this] [this f] "Return all entries in a region or all entries for a function."))

(def nocache-region
  (reify CacheRegion
    (conf [this] {:memento.core/type :memento.core/none})
    (cached [this f key args] (uncached this f args))
    (uncached [this f args] (apply f args))
    (if-cached [this f key] base/absent)
    (invalidate [this f] this)
    (invalidate [this f key] this)
    (invalidate-all [this] this)
    (put-all [this f keys-to-vals] this)
    (as-map [this] {})
    (as-map [this f] {})))

(defrecord RegionalCache [conf region-id key-fn wrapped-fn]
  Object
  (finalize [this] (invalidate-all this) nil)
  base/Cache
  (conf [this] conf)
  (cached [this args]
    (cached (*regions* region-id nocache-region) wrapped-fn (key-fn args) args))
  (uncached [this args]
    (uncached (*regions* region-id nocache-region) wrapped-fn args))
  (if-cached [this args]
    (if-cached (*regions* region-id nocache-region) wrapped-fn (key-fn args)))
  (original-function [this] (::f conf))
  (invalidate [this args]
    (invalidate (*regions* region-id nocache-region) wrapped-fn (key-fn args)) this)
  (invalidate-all [this]
    (invalidate (*regions* region-id nocache-region) wrapped-fn) this)
  (put-all [this args-to-vals]
    (put-all
      (*regions* region-id nocache-region)
      wrapped-fn
      (into {}
            (map (fn [entry] [(key-fn (key entry)) (val entry)]))
            args-to-vals))
    this)
  (as-map [this] (as-map this wrapped-fn)))

(defmethod impl/new-cache :memento.core/regional
  [{:memento.core/keys [seed region key-fn ret-fn] :as conf} f]
  (cond->
    (->RegionalCache
      (-> conf (assoc ::f f) (dissoc :memento.core/seed))
      region
      (impl/prepare-key-fn key-fn)
      (impl/prepare-fn f ret-fn))
    (map? seed) (base/put-all seed)))

(defmulti new-region :memento.core/type)

(defn create-region
  "Create CacheRegion instance based on conf. Typical constructor based on datastructure."
  [conf]
  (new-region (merge {:memento.core/type config/*default-type*} conf)))

(defn set-region!
  "Create a new Cache Region and bind it to region-id.

  It will overwrite existing region (generally dropping that cache)."
  [conf]
  (alter-var-root
    #'*regions*
    assoc
    (:memento.core/region conf)
    (new-region conf)))

(defn clear-region!
  "Invalidate entries in a cache region.

  If only region-id is supplied the whole cache region is cleared,
  otherwise the specified entry (function and args).

  The function used here needs to version."
  [region-id]
  (invalidate-all (get *regions* region-id nocache-region)))

(defn region-as-map
  "Return a map of memoized entries of the Cache Region."
  [region-id]
  (as-map (get *regions* region-id nocache-region)))

(defmacro with-region
  "Run forms with region-id bound to a new region (thread-local)."
  [region-conf & forms]
  `(binding [*regions* (assoc *regions* (:memento.core/region ~region-conf)
                                        (create-region ~region-conf))]
     ~@forms))

(defmacro with-region-alias
  "Run forms with region-alias aliasing the existing region region-id (thread-local)."
  [region-alias region-id & forms]
  `(binding [*regions* (assoc *regions* ~region-alias (get *regions* ~region-id))]
     ~@forms))

(defn cache
  "Create a cache configuration base for a region cache"
  [region]
  {:memento.core/type :memento.core/regional
   :memento.core/region region})
