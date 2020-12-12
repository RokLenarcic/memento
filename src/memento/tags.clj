(ns memento.tags
  "Tagged cache utilities.

  There's the main mapping of cache-ref to cache and aux index atom of
  tag to set of cache refs.

  Cache ref is either a var or a WeakReference to the function being cached."
  (:require [memento.base :as base]))

(def ^:dynamic *caches* "Contains map of cache-ref to cache instance" {})
(def tags (atom {}))

(defn assoc-cache-tags
  "Add cache to tag index"
  [index cache-tags ref]
  (reduce #(update %1 %2 (fnil conj #{}) ref) index cache-tags))

(defn dissoc-cache-tags
  "Remove cache from tag index"
  [index ref]
  (reduce-kv #(assoc %1 %2 (disj %3 ref)) {} index))

(defrecord TaggedCache [ref]
  Object
  (finalize [this]
    (swap! tags dissoc-cache-tags ref)
    (alter-var-root #'*caches* dissoc ref)
    nil)
  base/Cache
  (conf [this] (base/conf (*caches* this)))
  (cached [this args] (base/cached (*caches* this) args))
  (uncached [this args] (base/uncached (*caches* this) args))
  (if-cached [this args] (base/if-cached (*caches* this) args))
  (original-function [this] (base/original-function (*caches* this)))
  (invalidate [this args] (base/invalidate (*caches* this) args))
  (invalidate-all [this] (base/invalidate-all (*caches* this)))
  (put-all [this args-to-vals] (base/put-all (*caches* this) args-to-vals))
  (as-map [this] (base/as-map (*caches* this))))

(defn tagified-cache
  "Decorate a cache with tag handling."
  [cache]
  (if-let [tags (:memento.core/tags (base/conf cache))]
    (let [ref (Object.)]
      (alter-var-root #'*caches* assoc ref cache)
      (swap! tags assoc-cache-tags tags ref)
      (->TaggedCache ref))
    cache))
