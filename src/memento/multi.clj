(ns memento.multi
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b])
  (:import (memento.base ICache)
           (memento.multi ConsultingCache DaisyChainCache MultiCache TieredCache)))

(comment
  "A daisy chained cache.

  Entry is returned from cache IF PRESENT, otherwise upstream is hit. The returned value
  is NOT added to cache.

  After the operation the entry is either in local or upstream cache.")
(defmethod b/new-cache :memento.core/daisy [conf]
  (let [^ICache cache (b/base-create-cache (::cache conf))
        ^ICache upstream (b/base-create-cache (::upstream conf))]
    (DaisyChainCache. cache upstream conf b/absent)))

(comment
  "A tiered cache.

  Entry is fetched from cache, delegating to upstream is not found. After the operation
  the entry is in both caches.")

(defmethod b/new-cache :memento.core/tiered [conf]
  (let [^ICache cache (b/base-create-cache (::cache conf))
        ^ICache upstream (b/base-create-cache (::upstream conf))]
    (TieredCache. cache upstream conf b/absent)))

(comment
  "A consulting tiered cache.

  Entry is fetched from cache, if not found, the upstream is asked for entry if present (but not to make one
  in the upstream).

  After the operation, the entry is in local cache, upstream is unchanged.")

(defmethod b/new-cache :memento.core/consulting [conf]
  (let [^ICache cache (b/base-create-cache (::cache conf))
        ^ICache upstream (b/base-create-cache (::upstream conf))]
    (ConsultingCache. cache upstream conf b/absent)))

(defn delegate [^MultiCache multi-cache]
  (.getDelegate multi-cache))

(defn upstream [^MultiCache multi-cache]
  (.getUpstream multi-cache))
