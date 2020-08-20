(ns memento.guava
  "Guava cache implementation."
  (:require [memento.base :as b]
            [meta-merge.core :refer [meta-merge]])
  (:import (com.google.common.cache Cache CacheBuilder Weigher RemovalListener)
           (java.util.concurrent TimeUnit ExecutionException)
           (com.google.common.base Ticker)
           (com.google.common.util.concurrent UncheckedExecutionException)
           (memento.base NonCached)))

(def timeunits
  {:ns TimeUnit/NANOSECONDS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :s TimeUnit/SECONDS
   :m TimeUnit/MINUTES
   :h TimeUnit/HOURS
   :d TimeUnit/DAYS})

(defn parse-time-scalar
  "Returns the scalar part of time spec. Time can be specified by integer
  or a vector of two elements, where first element is an integer and the other is
  the time unit keyword."
  [time-param]
  (if (number? time-param) (long time-param) (first time-param)))

(defn ^TimeUnit parse-time-unit
  "Returns the time unit part of time spec. Time can be specified by integer
  or a vector of two elements, where first element is an integer and the other is
  the time unit keyword. If only integer is specified then time unit is seconds."
  [time-param]
  (timeunits (if (number? time-param) :s (second time-param))))

(defn cval->val "Change ::nil cache value to nil" [v]
  (when-not (= v ::nil) v))

(defn val->cval "Change nil to cache value of ::nil" [v]
  (if (some? v) v ::nil))

(defn process-non-cached
  "Unwrap NonCached objects and throw exception to prevent caching."
  [obj]
  (loop [v obj
         throw? false]
    (if (instance? NonCached v)
      (recur (:v v) true)
      (if throw? (throw (ex-info "" {::non-cached v})) v))))

(defn key->ckey "Transform key into cache key" [key-fn k]
  (key-fn (or k '())))

(defn ^CacheBuilder spec->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [concurrency initial-capacity size< weight< ttl fade refresh stats
                        kv-weight weak-keys weak-values soft-values ticker removal-listener]}]
  (cond-> (CacheBuilder/newBuilder)
    concurrency (.concurrencyLevel concurrency)
    initial-capacity (.initialCapacity initial-capacity)
    weight< (.maximumWeight weight<)
    size< (.maximumSize size<)
    kv-weight (.weigher (reify Weigher (weigh [_this k v]
                                         (kv-weight k (cval->val v)))))
    weak-keys (.weakKeys)
    weak-values (.weakValues)
    soft-values (.softValues)
    ttl (.expireAfterWrite (parse-time-scalar ttl) (parse-time-unit ttl))
    fade (.expireAfterAccess (parse-time-scalar fade) (parse-time-unit fade))
    refresh (.refreshAfterWrite (parse-time-scalar refresh) (parse-time-unit refresh))
    ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
    removal-listener (.removalListener (reify RemovalListener
                                         (onRemoval [this n]
                                           (removal-listener
                                             (.getKey n)
                                             (cval->val (.getValue n))
                                             (.getCause n)))))
    stats (.recordStats)))

(defn cget [^Cache guava-cache {:memento.core/keys [key-fn ret-fn]} f args]
  (try
    (.get guava-cache (key->ckey key-fn args)
          (fn cache-load []
            (->> args (apply f) ret-fn val->cval process-non-cached)))
    (catch ExecutionException e (throw (.getCause e)))
    (catch UncheckedExecutionException e
      (let [cause (.getCause e)
            data (ex-data cause)]
        (if (contains? data ::non-cached)
          (::non-cached data)
          (throw cause))))))

(defrecord GCache [^Cache guava-cache spec f]
  b/Cache
  (get-cached [this args]
    (cval->val (cget guava-cache spec f args)))
  (invalidate [this args]
    (.invalidate guava-cache (key->ckey (:memento.core/key-fn spec) args))
    this)
  (invalidate-all [this] (.invalidateAll guava-cache) this)
  (put-all [this args-to-vals]
    (let [{:memento.core/keys [key-fn]} spec]
      (reduce-kv (fn guava-put-key [^Cache c k v]
                   (.put c (key->ckey key-fn k) (val->cval v)))
                 guava-cache
                 args-to-vals)
      this))
  (as-map [this]
    (persistent!
      (reduce (fn [m e] (assoc! m (key e) (cval->val (val e))))
              (transient {})
              (.asMap guava-cache)))))

(defn augment-keyfn [key-fn region-cache]
  (fn [args] [(:f region-cache) (key->ckey (comp key-fn (:key-fn region-cache)) args)]))

;; Combine key fn and ret fn (RegionCache first then our)
(defrecord GRegion [^Cache guava-cache spec]
  b/CacheRegion
  (get-cached* [this region-cache args]
    (let [new-spec (-> spec
                       (update :memento.core/key-fn augment-keyfn region-cache)
                       (update :memento.core/ret-fn comp (:ret-fn region-cache)))]
      (cget guava-cache new-spec (:f region-cache) args)))
  (invalidate* [this region-cache args]
    (let [key-fn (augment-keyfn (:memento.core/key-fn spec) region-cache)]
      (.invalidate guava-cache (key-fn args)))
    this)
  (invalidate-cached [this region-cache]
    (doseq [e (b/as-map* this)]
      (let [k (key e)]
        (when (= (:f region-cache) (first k))
          (.invalidate guava-cache k))))
    this)
  (invalidate-region [this] (.invalidateAll guava-cache) this)
  (put-all* [this region-cache args-to-vals]
    (let [key-fn (augment-keyfn (:memento.core/key-fn spec) region-cache)]
      (reduce-kv
        (fn [_ k v]
          (.put guava-cache (key-fn k) (val->cval v)))
        nil
        args-to-vals))
    this)
  (cache-as-map [this {:keys [f]}]
    (persistent!
      (reduce (fn [m [k v]]
                (if (= f (first k))
                  (assoc! m (second k) (cval->val v))
                  m))
              (transient {})
              (.asMap guava-cache))))
  (as-map* [this]
    (persistent!
      (reduce (fn [m [k v]]
                (assoc! m k (cval->val v)))
              (transient {})
              (.asMap guava-cache)))))

(defmethod b/create-region :memento.core/guava [spec]
  (->GRegion (.build (spec->builder spec))
             (merge #:memento.core {:key-fn identity :ret-fn identity} spec)))

(defmethod b/create-cache :memento.core/guava
  [spec f]
  (let [seed (:memento.core/seed spec)]
    (cond->
      (->GCache (.build (spec->builder spec))
                (merge #:memento.core {:key-fn identity :ret-fn identity} spec)
                f)
      (map? seed) (b/put-all seed))))
