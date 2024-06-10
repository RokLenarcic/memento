(ns memento.caffeine
  "Caffeine cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b])
  (:import (java.util.concurrent TimeUnit)
           (memento.base Durations CacheKey EntryMeta ICache Segment)
           (com.github.benmanes.caffeine.cache Caffeine Weigher Ticker)
           (memento.caffeine CaffeineCache_ SecondaryIndex SpecialPromise Expiry)
           (memento.mount IMountPoint)))

(defn create-expiry
  "Assumes variable expiry is needed. So either ttl or fade is a function."
  [ttl fade ^Expiry cache-expiry]
  (let [read-default (some-> (or ttl fade) (Durations/nanos))
        write-default (Durations/nanos (or ttl fade [Long/MAX_VALUE :ns]))]
    (reify com.github.benmanes.caffeine.cache.Expiry
      (expireAfterCreate [this k v current-time]
        (if (instance? SpecialPromise v)
          Long/MAX_VALUE
          (.expireAfterUpdate this k v current-time Long/MAX_VALUE)))
      (expireAfterUpdate [this k v current-time current-duration]
        (if-let [ret (.ttl cache-expiry {} (.getArgs ^CacheKey k) v)]
          (Durations/nanos ret)
          (if-let [ret (.fade cache-expiry {} (.getArgs ^CacheKey k) v)]
            (Durations/nanos ret)
            write-default)))
      (expireAfterRead [this k v current-time current-duration]
        (if (instance? SpecialPromise v)
          current-duration
          ;; if fade is not specified, keep current validity (probably set by ttl)
          (if-let [ret (.fade cache-expiry {} (.getArgs ^CacheKey k) v)]
            (Durations/nanos ret)
            (or read-default current-duration)))))))

(defn conf->sec-index
  "Creates secondary index for evictions"
  [{:memento.core/keys [concurrency]}]
  (SecondaryIndex. (or concurrency 4)))

(defn ^Caffeine conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [initial-capacity size< ttl fade]
    :memento.caffeine/keys [weight< removal-listener kv-weight weak-keys weak-values
                            soft-values refresh stats ticker expiry]}]
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
    ;; these don't make sense as the caller cannot hold the CacheKey
    ;;weak-keys (.weakKeys)
    ;; careful around EntryMeta objects
    ;; mean that cached values have another wrapper yet again
    weak-values (.weakValues)
    soft-values (.softValues)
    expiry (.expireAfter (create-expiry ttl fade expiry))
    (and (not expiry) ttl) (.expireAfterWrite (Durations/nanos ttl) TimeUnit/NANOSECONDS)
    (and (not expiry) fade) (.expireAfterAccess (Durations/nanos fade) TimeUnit/NANOSECONDS)
    ;; not currently used because we don't build a loading cache
    refresh (.refreshAfterWrite (Durations/nanos refresh) TimeUnit/NANOSECONDS)
    ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
    stats (.recordStats)))

(defn assoc-imm-val!
  "If cached value is a completable future with immediately available value, assoc it to transient."
  [transient-m k v xf]
  (let [cv (if (instance? SpecialPromise v)
             (.getNow ^SpecialPromise v)
             v)]
    (if (identical? cv b/absent)
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
