(ns memento.guava
  "Guava cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.impl :as impl]
            [memento.base :as base]
            [memento.region :as region])
  (:import (clojure.lang ExceptionInfo)
           (com.google.common.cache Cache CacheBuilder Weigher RemovalListener)
           (com.google.common.base Ticker)
           (com.google.common.util.concurrent UncheckedExecutionException)
           (java.util Iterator)
           (java.util.concurrent ExecutionException)
           (memento.base DoNotCache)))

(defn cval->val "Change ::nil cache value to nil" [v]
  (when-not (= v ::nil) v))

(defn val->cval "Change nil to cache value of ::nil" [v]
  (if (some? v) v ::nil))

(defn process-non-cached
  "Unwrap NonCached objects and throw exception to prevent caching."
  [obj]
  (if (instance? DoNotCache obj) (throw (ExceptionInfo. "" {::non-cached obj})) obj))

(defn ^CacheBuilder conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [concurrency initial-capacity size< ttl fade]
    :memento.guava/keys [weight< removal-listener kv-weight weak-keys weak-values
                         soft-values refresh stats ticker]}
   region?]
  (cond-> (CacheBuilder/newBuilder)
    concurrency (.concurrencyLevel concurrency)
    initial-capacity (.initialCapacity initial-capacity)
    weight< (.maximumWeight weight<)
    size< (.maximumSize size<)
    kv-weight (.weigher
                (if region?
                  (reify Weigher (weigh [_this k v]
                                   (kv-weight (first k) (second k) (cval->val v))))
                  (reify Weigher (weigh [_this k v]
                                   (kv-weight k (cval->val v))))))
    weak-keys (.weakKeys)
    weak-values (.weakValues)
    soft-values (.softValues)
    ttl (.expireAfterWrite (impl/parse-time-scalar ttl) (impl/parse-time-unit ttl))
    fade (.expireAfterAccess (impl/parse-time-scalar fade) (impl/parse-time-unit fade))
    refresh (.refreshAfterWrite (impl/parse-time-scalar refresh) (impl/parse-time-unit refresh))
    ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
    removal-listener (.removalListener
                       (if region?
                         (reify RemovalListener
                           (onRemoval [this n]
                             (removal-listener
                               (first (.getKey n))
                               (second (.getKey n))
                               (cval->val (.getValue n))
                               (.getCause n))))
                         (reify RemovalListener
                           (onRemoval [this n]
                             (removal-listener
                               (.getKey n)
                               (cval->val (.getValue n))
                               (.getCause n))))))
    stats (.recordStats)))

(defn cget [^Cache guava-cache key f args]
  "Main get function for Guava Cache"
  (try
    (.get guava-cache key
          (fn cache-load []
            (->> args (apply f) process-non-cached val->cval)))
    (catch ExecutionException e (throw (.getCause e)))
    (catch UncheckedExecutionException e
      (let [cause (.getCause e)
            data (ex-data cause)]
        (if (contains? data ::non-cached)
          (::non-cached data)
          (throw cause))))))

;;;;;;;;;;;;;;;
(defrecord GCache [conf ^Cache guava-cache key-fn f]
  base/Cache
  (conf [this] conf)
  (cached [this args] (cval->val (cget guava-cache (key-fn args) f args)))
  (uncached [this args] (apply f args))
  (if-cached [this args]
    (if-some [v (.getIfPresent guava-cache (key-fn args))] v base/absent))
  (original-function [this] (::f conf))
  (invalidate [this args]
    (.invalidate guava-cache (key-fn args)) this)
  (invalidate-all [this] (.invalidateAll guava-cache) this)
  (put-all [this args-to-vals]
    (reduce-kv #(.put guava-cache (key-fn %2) (val->cval %3)) nil args-to-vals)
    this)
  (as-map [this]
    (persistent!
      (reduce (fn [m e] (assoc! m (key e) (cval->val (val e))))
              (transient {})
              (.asMap guava-cache)))))

(defmethod impl/new-cache :memento.core/guava
  [conf f]
  (let [seed (:memento.core/seed conf)]
    (cond->
      (->GCache (-> conf (assoc ::f f) (dissoc :memento.core/seed))
                (.build (conf->builder conf false))
                (impl/prepare-key-fn (:memento.core/key-fn conf))
                (impl/prepare-fn f (:memento.core/ret-fn conf)))
      (map? seed) (base/put-all seed))))

;;;;;;;;;;;;;; REGION
; ->key-fn take 2 args f and key
(defrecord GRegion [conf ^Cache guava-cache ->key-fn ret-fn]
  region/CacheRegion
  (conf [this] conf)
  (cached [this f key args]
    (cval->val (cget guava-cache (->key-fn f key) (comp ret-fn f) args)))
  (uncached [this f args] (apply (comp ret-fn f) args))
  (if-cached [this f key]
    (if-some [v (.getIfPresent guava-cache (->key-fn f key))] v base/absent))
  (invalidate [this f]
    (loop [^Iterator it (.. (.asMap guava-cache) keySet iterator)]
      (if (.hasNext it)
        (do (when (= f (first (.next it)))
              (.remove it))
            (recur it))
        this)))
  (invalidate [this f key] (.invalidate guava-cache (->key-fn f key)) this)
  (invalidate-all [this] (.invalidateAll guava-cache) this)
  (put-all [this f keys-to-vals]
    (reduce-kv #(.put guava-cache (->key-fn f %2) (val->cval %3)) nil keys-to-vals)
    this)
  (as-map [this] (persistent!
                   (reduce (fn [m [k v]]
                             (assoc! m k (cval->val v)))
                           (transient {})
                           (.asMap guava-cache))))
  (as-map [this f]
    (persistent!
      (reduce (fn [m [k v]]
                (if (= f (first k))
                  (assoc! m k (cval->val v))
                  m))
              (transient {})
              (.asMap guava-cache)))))

(defmethod region/new-region :memento.core/guava [conf]
  (->GRegion conf
             (.build (conf->builder conf true))
             (if-let [key-fn (:memento.core/key-fn conf)]
               (fn [f key] [f (key-fn key)])
               vector)
             (:memento.core/ret-fn conf identity)))

(defn stats
  "Return guava stats for the function, if function has non-regional stated Guava Cache.

   Returns com.google.common.cache.CacheStats"
  [cached-fn]
  (when-let [c (impl/active-cache cached-fn)]
    (when (instance? GCache c)
      (.stats ^Cache (:guava-cache c)))))

(defn region-stats
  "Return guava stats for the region-id. Returns com.google.common.cache.CacheStats"
  [region-id]
  (when-let [c (region/*regions* region-id)]
    (when (instance? GRegion c)
      (.stats ^Cache (:guava-cache c)))))
