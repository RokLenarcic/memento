(ns memento.caffeine
  "Caffeine cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as b])
  (:import (clojure.lang AFn)
           (java.util Iterator)
           (java.util.concurrent ConcurrentHashMap CompletableFuture ExecutionException)
           (memento.base CacheKey EntryMeta ICache Segment)
           (com.github.benmanes.caffeine.cache Caffeine Weigher Ticker AsyncCache)
           (memento.caffeine SecondaryIndexOps)
           (memento.mount IMountPoint)))

(defn conf->sec-index
  "Creates secondary index for evictions"
  [{:memento.core/keys [concurrency initial-capacity]}]
  (ConcurrentHashMap. (or initial-capacity 16)
                      (float 0.75)
                      (or concurrency 4)))

(defn ^Caffeine conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [initial-capacity size< ttl fade]
    :memento.caffeine/keys [weight< removal-listener kv-weight weak-keys weak-values
                            soft-values refresh stats ticker]}
   sec-index]
  (let [listener (if removal-listener
                   (SecondaryIndexOps/listener sec-index removal-listener)
                   (SecondaryIndexOps/listener sec-index))]
    (cond-> (.removalListener (Caffeine/newBuilder) listener)
      initial-capacity (.initialCapacity initial-capacity)
      weight< (.maximumWeight weight<)
      size< (.maximumSize size<)
      kv-weight (.weigher
                  (reify Weigher (weigh [_this k v]
                                   (kv-weight (.getId ^CacheKey k)
                                              (.getArgs ^CacheKey k)
                                              (b/unwrap-meta v)))))
      weak-keys (.weakKeys)
      ;; These are disallowed in Async caches
      ;weak-values (.weakValues)
      ;soft-values (.softValues)
      ttl (.expireAfterWrite (b/parse-time-scalar ttl) (b/parse-time-unit ttl))
      fade (.expireAfterAccess (b/parse-time-scalar fade) (b/parse-time-unit fade))
      ;; not currently used because we don't build a loading cache
      refresh (.refreshAfterWrite (b/parse-time-scalar refresh) (b/parse-time-unit refresh))
      ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
      stats (.recordStats))))

(defn val->cval
  "Converts val into cache friendly version. The problem is that AsyncCache has 'smart' feature where
  it will delete entries where CompletableFuture has nil value or exception value."
  [v]
  (CompletableFuture/completedFuture (if (nil? v) (EntryMeta. nil false #{}) v)))

(defn assoc-imm-val!
  "If cached value is a completable future with immediately available value, assoc it to transient."
  [transient-m k ^CompletableFuture v xf]
  (let [cv (.getNow v b/absent)]
    (if (= cv b/absent)
      transient-m
      (assoc! transient-m k (xf cv)))))

;;;;;;;;;;;;;;
; ->key-fn take 2 args f and key
(defrecord CaffeineCache [conf ^AsyncCache caffeine-cache ->key-fn ret-fn ^ConcurrentHashMap sec-index]
  ICache
  (conf [this] conf)
  (cached [this segment args]
    (b/unwrap-meta
      (let [f (if ret-fn (fn [& args] (ret-fn args (AFn/applyToHelper (.getF segment) args))) (.getF segment))
            k (->key-fn segment args)
            fut (CompletableFuture.)]
        (if-some [^CompletableFuture prev-fut (-> caffeine-cache .asMap (.putIfAbsent k fut))]
          (try
            (.join prev-fut)
            (catch ExecutionException e
              (throw (.getCause e))))
          (try
            (let [result (AFn/applyToHelper f args)]
              (SecondaryIndexOps/secIndexConj sec-index k result)
              (when (and (instance? EntryMeta result) (.isNoCache ^EntryMeta result))
                (.remove (.asMap caffeine-cache) k))
              (.complete fut (if (nil? result) (EntryMeta. nil false #{}) result))
              result)
            (catch Throwable t
              (.completeExceptionally fut t)
              (throw t)))))))
  (ifCached [this segment args]
    (if-some [v (.getIfPresent caffeine-cache (->key-fn segment args))]
      (b/unwrap-meta (.getNow ^CompletableFuture v b/absent))
      b/absent))
  (invalidate [this segment]
    (loop [^Iterator it (.. (.asMap caffeine-cache) keySet iterator)]
      (if (.hasNext it)
        (do (when (= (.getId segment) (.getId ^CacheKey (.next it)))
              (.remove it))
            (recur it))
        this)))
  (invalidate [this segment args] (.invalidate (.synchronous caffeine-cache) (->key-fn segment args)) this)
  (invalidateAll [this] (.invalidateAll (.synchronous caffeine-cache)) this)
  (invalidateId [this id]
    (when-let [cache-keys (.remove sec-index id)]
      (.invalidateAll (.synchronous caffeine-cache) cache-keys))
    this)
  (addEntries [this segment args-to-vals]
    (reduce-kv #(let [k (->key-fn segment %2)
                      _ (SecondaryIndexOps/secIndexConj sec-index k %3)]
                  (.put caffeine-cache k (val->cval %3)))
               nil
               args-to-vals)
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
  (let [sec-index (conf->sec-index conf)]
    (->CaffeineCache conf
              (.buildAsync (conf->builder conf sec-index))
              (if-let [key-fn (:memento.core/key-fn conf)]
                (fn [^Segment segment args] (CacheKey. (.getId segment) (key-fn ((.getKeyFn segment) args))))
                (fn [^Segment segment args] (CacheKey. (.getId segment) ((.getKeyFn segment) args))))
              (:memento.core/ret-fn conf)
              sec-index)))

(defn stats
  "Return caffeine stats for the cache if it is a caffeine Cache.

   Takes a memoized fn or a Cache instance as a parameter.

   Returns com.github.benmanes.caffeine.cache.stats.CacheStats"
  [fn-or-cache]
  (if (instance? ICache fn-or-cache)
    (when (instance? CaffeineCache fn-or-cache)
      (.stats (.synchronous ^AsyncCache (:caffeine-cache fn-or-cache))))
    (stats (.mountedCache ^IMountPoint fn-or-cache))))

(defn to-data [cache]
  (when-let [caffeine (:caffeine-cache cache)]
    (persistent!
      (reduce (fn [m [^CacheKey k v]] (assoc-imm-val! m [(.getId k) (.getArgs k)] v #(if (and (instance? EntryMeta %) (nil? (.getV ^EntryMeta %)))
                                                                                       nil %)))
              (transient {})
              (.asMap ^AsyncCache caffeine)))))

(defn load-data [cache data-map]
  (when-let [caffeine (:caffeine-cache cache)]
    (reduce-kv
      (fn [^AsyncCache c k v]
        (SecondaryIndexOps/secIndexConj (:sec-index cache) k v)
        (.put c (CacheKey. (first k) (second k)) (val->cval v))
        c)
      caffeine
      data-map)))
