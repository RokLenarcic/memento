(ns memento.caffeine
  "Caffeine cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.mount :as mount]
            [memento.base :as b])
  (:import (java.util Iterator HashSet)
           (java.util.concurrent ConcurrentHashMap CompletableFuture ExecutionException)
           (memento.base EntryMeta)
           (java.util.function BiFunction)
           (com.github.benmanes.caffeine.cache RemovalListener Caffeine Weigher Ticker AsyncCache)))

(defrecord CacheKey [id args])

(def wrapped-nil (b/->EntryMeta nil false #{}))

(defn conf->sec-index
  "Creates secondary index for evictions"
  [{:memento.core/keys [concurrency initial-capacity]}]
  (ConcurrentHashMap. (or initial-capacity 16)
                      (float 0.75)
                      (or concurrency 4)))

(defn sec-index-conj-entry
  "Add entry to secondary index.
  k is CacheKey of incoming Cache entry
  v is value of incoming cache entry, might be EntryMeta, if it is then we use each tag-idents
  as key (id) pointing to a HashSet of CacheKeys.

  For each ID we add CacheKey to its HashSet."
  [^ConcurrentHashMap sec-index k v]
  (let [conj-bifunction (reify BiFunction
                          (apply [this _ prev-v]
                            (doto (or ^HashSet prev-v (HashSet.))
                              (.add k))))]
    (doseq [id (when (instance? EntryMeta v) (:tag-idents v))]
      (.compute sec-index id conj-bifunction))
    v))

(defn sec-index-disj-entry
  "Remove value from secondary index, processing EntityMeta if there is one.

  k is CacheKey of Cache entry being removed
  v is value of Cache entry, might be EntryMeta, if it is then we use each of tag-idents as key (id)
  pointing to a HashSet. We remove CacheKey from each set, removing the whole entry if the resulting
  set is empty."
  [^ConcurrentHashMap sec-index k v]
  (let [disj-bifunction (reify BiFunction
                          (apply [this _ hash-set]
                            (.remove ^HashSet hash-set k)
                            (when-not (.isEmpty ^HashSet hash-set) hash-set)))]
    (doseq [id (when (instance? EntryMeta v) (:tag-idents v))]
      (.computeIfPresent sec-index id disj-bifunction))))

(defn ^Caffeine conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [initial-capacity size< ttl fade]
    :memento.caffeine/keys [weight< removal-listener kv-weight weak-keys weak-values
                            soft-values refresh stats ticker]}
   sec-index]
  (let [listener (if removal-listener
                   (reify RemovalListener
                     (onRemoval [this k v reason]
                       (sec-index-disj-entry sec-index k v)
                       (removal-listener
                         (.id ^CacheKey k)
                         (.args ^CacheKey k)
                         (b/unwrap-meta v)
                         reason)))
                   (reify RemovalListener
                     (onRemoval [this k v reason]
                       (sec-index-disj-entry sec-index k v))))]
    (cond-> (.removalListener (Caffeine/newBuilder) listener)
      initial-capacity (.initialCapacity initial-capacity)
      weight< (.maximumWeight weight<)
      size< (.maximumSize size<)
      kv-weight (.weigher
                  (reify Weigher (weigh [_this k v]
                                   (kv-weight (.id ^CacheKey k)
                                              (.args ^CacheKey k)
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
  (CompletableFuture/completedFuture (if (nil? v) wrapped-nil v)))

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
  b/Cache
  (conf [this] conf)
  (cached [this segment args]
    (b/unwrap-meta
      (let [f (if ret-fn (fn [& args] (ret-fn args (apply (:f segment) args))) (:f segment))
            k (->key-fn segment args)
            fut (CompletableFuture.)]
        (if-some [^CompletableFuture prev-fut (-> caffeine-cache .asMap (.putIfAbsent k fut))]
          (try
            (.join prev-fut)
            (catch ExecutionException e
              (throw (.getCause e))))
          (try
            (let [result (apply f args)]
              (sec-index-conj-entry sec-index k result)
              (.complete fut (when-not (and (instance? EntryMeta result) (:no-cache? result))
                               (if (nil? result) wrapped-nil result)))
              result)
            (catch Throwable t
              (.completeExceptionally fut t)
              (throw t)))))))
  (if-cached [this segment args]
    (if-some [v (.getIfPresent caffeine-cache (->key-fn segment args))]
      (b/unwrap-meta (.getNow ^CompletableFuture v b/absent))
      b/absent))
  (invalidate [this segment]
    (loop [^Iterator it (.. (.asMap caffeine-cache) keySet iterator)]
      (if (.hasNext it)
        (do (when (= (:id segment) (.id ^CacheKey (.next it)))
              (.remove it))
            (recur it))
        this)))
  (invalidate [this segment args] (.invalidate (.synchronous caffeine-cache) (->key-fn segment args)) this)
  (invalidate-all [this] (.invalidateAll (.synchronous caffeine-cache)) this)
  (invalidate-id [this id]
    (when-let [cache-keys (.remove sec-index id)]
      (.invalidateAll (.synchronous caffeine-cache) cache-keys))
    this)
  (put-all [this segment args-to-vals]
    (reduce-kv #(let [k (->key-fn segment %2)
                      _ (sec-index-conj-entry sec-index k %3)]
                  (.put caffeine-cache k (val->cval %3)))
               nil
               args-to-vals)
    this)
  (as-map [this] (persistent!
                   (reduce (fn [m [k v]] (assoc-imm-val! m k v b/unwrap-meta))
                           (transient {})
                           (.asMap caffeine-cache))))
  (as-map [this segment]
    (persistent!
      (reduce (fn [m [^CacheKey k v]]
                (if (= (:id segment) (.id k)) (assoc-imm-val! m (.args k) v b/unwrap-meta)
                  m))
              (transient {})
              (.asMap caffeine-cache)))))

(defmethod b/new-cache :memento.core/caffeine [conf]
  (let [sec-index (conf->sec-index conf)]
    (->CaffeineCache conf
              (.buildAsync (conf->builder conf sec-index))
              (if-let [key-fn (:memento.core/key-fn conf)]
                (fn [segment args] (->CacheKey (:id segment) (key-fn ((:key-fn segment) args))))
                (fn [segment args] (->CacheKey (:id segment) ((:key-fn segment) args))))
              (:memento.core/ret-fn conf)
              sec-index)))

(defn stats
  "Return caffeine stats for the cache if it is a caffeine Cache.

   Takes a memoized fn or a Cache instance as a parameter.

   Returns com.github.benmanes.caffeine.cache.stats.CacheStats"
  [fn-or-cache]
  (if (satisfies? b/Cache fn-or-cache)
    (when (instance? CaffeineCache fn-or-cache)
      (.stats (.synchronous ^AsyncCache (:caffeine-cache fn-or-cache))))
    (when-let [c (mount/mount-point fn-or-cache)]
      (stats (mount/mounted-cache c)))))

(defn to-data [{:keys [caffeine-cache] :as _cache}]
  (persistent!
    (reduce (fn [m [^CacheKey k v]] (assoc-imm-val! m [(.id k) (.args k)] v #(when-not (= % wrapped-nil) %)))
            (transient {})
            (.asMap ^AsyncCache caffeine-cache))))

(defn load-data [cache data-map]
  (reduce-kv
    (fn [^AsyncCache c k v]
      (.put c (->CacheKey (first k) (second k)) (val->cval v))
      c)
    (:caffeine-cache cache)
    data-map))
