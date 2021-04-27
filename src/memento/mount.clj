(ns memento.mount
  "Mount points, they serve as glue between a cache that can house entries from
  multiple functions and the individual functions."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.config :as config]))

(def ^:dynamic *caches* "Contains map of mount point to cache instance" {})
(def tags "Map tag to mount-point" (atom {}))

(def configuration-props [config/key-fn config/ret-fn config/seed config/tags
                          config/evt-fn])

(defn assoc-cache-tags
  "Add Mount Point ref to tag index"
  [index cache-tags ref]
  (reduce #(update %1 %2 (fnil conj #{}) ref) index cache-tags))

(defn dissoc-cache-tags
  "Remove Mount Point ref from tag index"
  [index ref]
  (reduce-kv #(assoc %1 %2 (disj %3 ref)) {} index))

(deftype ReloadGuard [cache-mount]
  Object
  (finalize [this]
    (swap! tags dissoc-cache-tags cache-mount)
    (alter-var-root #'*caches* dissoc cache-mount)
    nil))

(defprotocol MountPoint
  "Protocol for cache mount"
  (as-map [this] "Returns the cache as a map. This does not imply a snapshot,
  as implementation might provide a weakly consistent view of the cache.")
  (cached [this args] "Return cached value, possibly invoking the function with the args to
    obtain the value. This should be a thread-safe atomic operation.")
  (get-tags [this] "Coll of tags for this mount point")
  (handle-event [this evt] "Handles event using internal event handling mechanism, usually a function")
  (invalidate [this args] "Invalidate entry for args, returns Cache")
  (invalidate-all [this] "Invalidate all entries, returns Cache")
  (mounted-cache [this] "Returns currently mounted Cache.")
  (put-all [this args-to-vals] "Add entries to cache, returns Cache")
  (original-function [this] "Returns the original function"))

(defrecord UntaggedMountPoint [cache segment evt-handler]
  MountPoint
  (as-map [this] (base/as-map cache segment))
  (cached [this args] (base/cached cache segment args))
  (get-tags [this] [])
  (handle-event [this evt] (evt-handler this evt))
  (invalidate [this args] (base/invalidate cache segment args))
  (invalidate-all [this] (base/invalidate cache segment))
  (mounted-cache [this] cache)
  (put-all [this args-to-vals] (base/put-all cache segment args-to-vals))
  (original-function [this] (:f segment)))

(defrecord TaggedMountPoint [tags segment evt-handler]
  MountPoint
  (as-map [this] (base/as-map (*caches* this base/no-cache) segment))
  (cached [this args] (base/cached (*caches* this base/no-cache) segment args))
  (get-tags [this] tags)
  (handle-event [this evt] (evt-handler this evt))
  (invalidate [this args] (base/invalidate (*caches* this base/no-cache) segment args))
  (invalidate-all [this] (base/invalidate (*caches* this base/no-cache) segment))
  (mounted-cache [this] (*caches* this base/no-cache))
  (put-all [this args-to-vals]
    (base/put-all (*caches* this base/no-cache) segment args-to-vals))
  (original-function [this] (:f segment)))

(defn reify-mount-conf
  "Transform user given mount-conf to a canonical form of a map."
  [mount-conf]
  (if (map? mount-conf)
    mount-conf
    {config/tags ((if (sequential? mount-conf) vec vector) mount-conf)}))

(defn create-mount
  "Create mount record by specified map conf"
  [f cache mount-conf]
  (let [key-fn (config/key-fn mount-conf identity)
        evt-fn (config/evt-fn mount-conf (fn [_ _] nil))
        f* (if-let [ret-fn (config/ret-fn mount-conf)]
             (fn [& args] (ret-fn args (apply f args)))
             f)
        segment (base/->Segment f* key-fn (get mount-conf ::name f))]
    (if-let [t (config/tags mount-conf)]
      (let [wrapped-t (if (sequential? t) t (vector t))
            mp (->TaggedMountPoint wrapped-t segment evt-fn)]
        (alter-var-root #'*caches* assoc mp cache)
        (swap! tags assoc-cache-tags wrapped-t mp)
        mp)
      (->UntaggedMountPoint cache segment evt-fn))))

(defn bind
  "Bind a cache to a fn or var. Internal function."
  [fn-or-var mount-conf cache]
  (if (var? fn-or-var)
    (alter-var-root fn-or-var bind (assoc (reify-mount-conf mount-conf) ::name (str fn-or-var)) cache)
    (let [mount-conf (reify-mount-conf mount-conf)
          cache-mount (create-mount fn-or-var cache mount-conf)
          reload-guard (when (and config/reload-guards? (config/tags mount-conf))
                         (ReloadGuard. cache-mount))]
      (put-all cache-mount (config/seed mount-conf {}))
      (with-meta
        (fn [& args]
          ; don't let clojure compiler clear reload-guard local until this function
          ; gets garbage collected
          (identity reload-guard)
          (cached cache-mount args))
        (merge (meta fn-or-var) {::mount cache-mount})))))

(defn mount-point
  "Return active mount point from the object's meta."
  [obj]
  (if (satisfies? MountPoint obj) obj (::mount (meta obj))))

(defn update-existing
  "Convenience function. Updates ks's that are present with the provided update fn."
  [m ks update-fn]
  (reduce #(if-let [kv (find %1 %2)] (assoc %1 %2 (update-fn (val kv))) %1) m ks))

(defn alter-caches-mapping
  "Internal function. Modifies entire tagged cache map with the provided function.
   Applies the function as (fn [*caches* refs & other-update-fn-args])"
  [tag update-fn & update-fn-args]
  (let [refs (get @tags tag [])
        update-fn #(apply update-fn % refs update-fn-args)]
    (if (.getThreadBinding #'*caches*)
      (var-set #'*caches* (update-fn *caches*))
      (alter-var-root #'*caches* update-fn))))
