(ns memento.multi
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b]))

(defrecord MultiCache [cache upstream conf cached-fn]
  b/Cache
  (conf [this] conf)
  (cached [this segment args]
    (cached-fn segment args))
  (if-cached [this segment args]
    (let [v1 (b/if-cached cache segment args)]
      (if (= b/absent v1)
        (b/if-cached upstream segment args)
        v1)))
  (invalidate [this segment]
    (b/invalidate upstream segment)
    (b/invalidate cache segment))
  (invalidate [this segment args]
    (b/invalidate upstream segment args)
    (b/invalidate cache segment args))
  (invalidate-all [this]
    (b/invalidate-all upstream)
    (b/invalidate-all cache))
  (invalidate-id [this id]
    (b/invalidate-id upstream id)
    (b/invalidate-id cache id))
  (put-all [this f args-to-vals] (b/put-all cache f args-to-vals))
  (as-map [this] (b/as-map cache))
  (as-map [this segment] (b/as-map cache segment)))

(comment
  "A daisy chained cache.

  Entry is returned from cache IF PRESENT, otherwise upstream is hit. The returned value
  is NOT added to cache.

  After the operation the entry is either in local or upstream cache.")
(defmethod b/new-cache :memento.core/daisy [conf]
  (let [cache (b/base-create-cache (::cache conf))
        upstream (b/base-create-cache (::upstream conf))]
    (->MultiCache cache upstream conf (fn [segment args]
                                        (let [cached (b/if-cached cache segment args)]
                                          (if (= b/absent cached)
                                            (b/cached upstream segment args)
                                            cached))))))

(comment
  "A tiered cache.

  Entry is fetched from cache, delegating to upstream is not found. After the operation
  the entry is in both caches.")

(defmethod b/new-cache :memento.core/tiered [conf]
  (let [cache (b/base-create-cache (::cache conf))
        upstream (b/base-create-cache (::upstream conf))
        cached-fn (fn [segment args]
                    ; function to poll upstream
                    (let [ask-upstream (fn [& _] (b/cached upstream segment args))]
                      ; if cache doesn't have it cached, poll upstream
                      ; achieved by changing the function
                      (b/cached cache (assoc segment :f ask-upstream) args)))]
    (->MultiCache cache upstream conf cached-fn)))

(comment
  "A consulting tiered cache.

  Entry is fetched from cache, if not found, the upstream is asked for entry if present (but not to make one
  in the upstream).

  After the operation, the entry is in local cache, upstream is unchanged.")

(defmethod b/new-cache :memento.core/consulting [conf]
  (let [cache (b/base-create-cache (::cache conf))
        upstream (b/base-create-cache (::upstream conf))
        cached-fn (fn [segment args]
                    ;; poll upstream with if-present semantics
                    (let [upstream-or-calc (fn [oldf]
                                             (fn [& processed-args]
                                               (let [up-val (b/if-cached upstream segment args)]
                                                 (if (= b/absent up-val)
                                                   (apply oldf processed-args)
                                                   up-val))))]
                      (b/cached cache (update segment :f upstream-or-calc) args)))]
    (->MultiCache cache upstream conf cached-fn)))
