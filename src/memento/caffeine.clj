(ns memento.caffeine
  "Caffeine cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b])
  (:import (memento.base CacheKey EntryMeta ICache Segment)
           (com.github.benmanes.caffeine.cache Caffeine Weigher Ticker)
           (memento.caffeine CaffeineCache_ SecondaryIndex SpecialPromise)
           (memento.mount IMountPoint)))

(defn conf->sec-index
  "Creates secondary index for evictions"
  [{:memento.core/keys [concurrency]}]
  (SecondaryIndex. (or concurrency 4)))

(defn ^Caffeine conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [initial-capacity size< ttl fade]
    :memento.caffeine/keys [weight< removal-listener kv-weight weak-keys weak-values
                            soft-values refresh stats ticker]}]
  (cond-> (Caffeine/newBuilder)
    removal-listener (.removalListener (CaffeineCache_/listener removal-listener))
    initial-capacity (.initialCapacity initial-capacity)
    weight< (.maximumWeight weight<)
    size< (.maximumSize size<)
    kv-weight (.weigher
                (reify Weigher (weigh [_this k v]
                                 (kv-weight (.getId ^CacheKey k)
                                            (.getArgs ^CacheKey k)
                                            (b/unwrap-meta v)))))
    weak-keys (.weakKeys)
    ;; these don't make sense as the caller cannot hold the promises we use, also EntryMeta objects
    ;; mean that cached values have another wrapper yet again
    ;weak-values (.weakValues)
    soft-values (.softValues)
    ttl (.expireAfterWrite (b/parse-time-scalar ttl) (b/parse-time-unit ttl))
    fade (.expireAfterAccess (b/parse-time-scalar fade) (b/parse-time-unit fade))
    ;; not currently used because we don't build a loading cache
    refresh (.refreshAfterWrite (b/parse-time-scalar refresh) (b/parse-time-unit refresh))
    ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
    stats (.recordStats)))

(defn assoc-imm-val!
  "If cached value is a completable future with immediately available value, assoc it to transient."
  [transient-m k v xf]
  (let [cv (if (instance? SpecialPromise v)
             (.getNow v b/absent)
             v)]
    (if (= cv b/absent)
      transient-m
      (assoc! transient-m k (xf cv)))))

;;;;;;;;;;;;;;
; ->key-fn take 2 args f and key
(defrecord CaffeineCache [conf ^CaffeineCache_ caffeine-cache]
  ICache
  (conf [this] conf)
  (cached [this segment args]
    (.cached caffeine-cache segment args))
  (ifCached [this segment args]
    (.ifCached caffeine-cache segment args))
  (invalidate [this segment]
    (.invalidate caffeine-cache ^Segment segment)
    this)
  (invalidate [this segment args] (.invalidate caffeine-cache ^Segment segment args)
    this)
  (invalidateAll [this] (.invalidateAll caffeine-cache) this)
  (invalidateIds [this ids]
    (.invalidateIds caffeine-cache ids)
    this)
  (addEntries [this segment args-to-vals]
    (.addEntries caffeine-cache segment args-to-vals)
    this)
  (asMap [this] (persistent!
                  (reduce (fn [m [k v]] (assoc-imm-val! m k v b/unwrap-meta))
                          (transient {})
                          (.asMap caffeine-cache))))
  (asMap [this segment]
    (persistent!
      (reduce (fn [m [^CacheKey k v]]
                (if (= (.getId segment) (.getId k)) (assoc-imm-val! m (.getArgs k) v b/unwrap-meta)
                  m))
              (transient {})
              (.asMap caffeine-cache)))))

(defmethod b/new-cache :memento.core/caffeine [conf]
  (->CaffeineCache conf (CaffeineCache_.
                          (conf->builder conf)
                          (:memento.core/key-fn conf)
                          (:memento.core/ret-fn conf)
                          (:memento.core/ret-ex-fn conf)
                          (conf->sec-index conf))))

(defn stats
  "Return caffeine stats for the cache if it is a caffeine Cache.

   Takes a memoized fn or a Cache instance as a parameter.

   Returns com.github.benmanes.caffeine.cache.stats.CacheStats"
  [fn-or-cache]
  (if (instance? ICache fn-or-cache)
    (when (instance? CaffeineCache fn-or-cache)
      (.stats ^CaffeineCache_ (:caffeine-cache fn-or-cache)))
    (stats (.mountedCache ^IMountPoint fn-or-cache))))

(defn to-data [cache]
  (when-let [caffeine (:caffeine-cache cache)]
    (persistent!
      (reduce (fn [m [^CacheKey k v]] (assoc-imm-val! m
                                                      [(.getId k) (.getArgs k)]
                                                      v
                                                      #(if (and (instance? EntryMeta %) (nil? (.getV ^EntryMeta %)))
                                                         nil %)))
              (transient {})
              (.asMap ^CaffeineCache_ caffeine)))))

(defn load-data [cache data-map]
  (.loadData ^CaffeineCache_ (:caffeine-cache cache) data-map)
  cache)
