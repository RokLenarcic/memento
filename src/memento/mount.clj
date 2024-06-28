(ns memento.mount
  "Mount points, they serve as glue between a cache that can house entries from
  multiple functions and the individual functions."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.config :as config])
  (:import (clojure.lang AFn ISeq)
           (memento.base ICache Segment)
           (memento.mount CachedFn IMountPoint)))

(def ^:dynamic *caches* "Contains map of mount point to cache instance" {})
(def tags "Map tag to mount-point" (atom {}))

(def configuration-props [config/key-fn config/ret-fn config/seed config/tags
                          config/evt-fn config/id config/key-fn* config/ret-ex-fn])

(defn assoc-cache-tags
  "Add Mount Point ref to tag index"
  [index cache-tags ref]
  (reduce #(update %1 %2 (fnil conj #{}) ref) index cache-tags))

(defn dissoc-cache-tags
  "Remove Mount Point ref from tag index"
  [index ref]
  (reduce-kv #(assoc %1 %2 (disj %3 ref)) {} index))

(deftype TagsUnloader [cache-mount]
  Runnable
  (run [this]
    (swap! tags dissoc-cache-tags cache-mount)
    (alter-var-root #'*caches* dissoc cache-mount)
    nil))

(defrecord UntaggedMountPoint [^ICache cache ^Segment segment evt-handler]
  IMountPoint
  (asMap [this] (.asMap cache segment))
  (cached [this args] (.cached cache segment args))
  (ifCached [this args] (.ifCached cache segment args))
  (getTags [this] [])
  (handleEvent [this evt] (evt-handler this evt))
  (invalidate [this args] (.invalidate cache segment args))
  (invalidateAll [this] (.invalidate cache segment))
  (mountedCache [this] cache)
  (addEntries [this args-to-vals] (.addEntries cache segment args-to-vals))
  (segment [this] segment))

(defrecord TaggedMountPoint [tags ^Segment segment evt-handler]
  IMountPoint
  (asMap [this] (.asMap ^ICache (*caches* this base/no-cache) segment))
  (cached [this args] (.cached ^ICache (*caches* this base/no-cache) segment args))
  (ifCached [this args] (.ifCached ^ICache (*caches* this base/no-cache) segment args))
  (getTags [this] tags)
  (handleEvent [this evt] (evt-handler this evt))
  (invalidate [this args] (.invalidate ^ICache (*caches* this base/no-cache) segment args))
  (invalidateAll [this] (.invalidate ^ICache (*caches* this base/no-cache) segment))
  (mountedCache [this] (*caches* this base/no-cache))
  (addEntries [this args-to-vals]
    (.addEntries ^ICache (*caches* this base/no-cache) segment args-to-vals))
  (segment [this] segment))

(defn mounted-cache [^IMountPoint mp] (.mountedCache mp))

(defn reify-mount-conf
  "Transform user given mount-conf to a canonical form of a map."
  [mount-conf]
  (if (map? mount-conf)
    mount-conf
    {config/tags ((if (sequential? mount-conf) vec vector) mount-conf)}))

(defn wrap-fn
  [f ret-fn ret-ex-fn]
  (cond
    (and ret-fn ret-ex-fn) (fn [& args]
                             (try (ret-fn args (AFn/applyToHelper f args))
                                  (catch Throwable t (throw (ret-ex-fn args t)))))
    ret-fn (fn [& args] (ret-fn args (AFn/applyToHelper f args)))
    ret-ex-fn (fn [& args]
                (try (AFn/applyToHelper f args)
                     (catch Throwable t (throw (ret-ex-fn args t)))))
    :else f))

(defn create-mount
  "Create mount record by specified map conf"
  [f cache mount-conf]
  (let [key-fn (or (config/key-fn mount-conf)
                   (when-let [base (config/key-fn* mount-conf)]
                     (fn [args] (AFn/applyToHelper base (if (instance? ISeq args) args (seq args)))))
                   identity)
        evt-fn (config/evt-fn mount-conf (fn [_ _] nil))
        f* (wrap-fn f (config/ret-fn mount-conf) (config/ret-ex-fn mount-conf))
        segment (Segment. f* key-fn (mount-conf config/id f) mount-conf)]
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
    (let [mount-conf (-> mount-conf
                         reify-mount-conf
                         (update config/id #(or % (.intern (str fn-or-var)))))]
      (alter-var-root fn-or-var bind mount-conf cache))
    (let [mount-conf (reify-mount-conf mount-conf)
          stacking (if (instance? CachedFn fn-or-var) (config/bind-mode mount-conf :new) :none)
          ^IMountPoint cache-mount (case stacking
                                     :new (create-mount (.getOriginalFn ^CachedFn fn-or-var) cache mount-conf)
                                     :keep (.getMp ^CachedFn fn-or-var)
                                     (:none :stack) (create-mount fn-or-var cache mount-conf))
          reload-guard (when (and config/reload-guards? (config/tags mount-conf) (not= :keep stacking))
                         (doto (Object.)
                           (IMountPoint/register (->TagsUnloader cache-mount))))
          f (case stacking
              :keep fn-or-var
              (:new :stack) (CachedFn. reload-guard cache-mount (meta fn-or-var) (.getOriginalFn ^CachedFn fn-or-var))
              :none (CachedFn. reload-guard cache-mount (meta fn-or-var) fn-or-var))]
      (.addEntries (.getMp ^CachedFn f) (config/seed mount-conf {}))
      f)))

(defn mount-point
  "Return active mount point from the object's meta."
  [obj]
  (when (instance? IMountPoint obj) obj))

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
