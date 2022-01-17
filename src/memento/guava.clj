(ns memento.guava
  "Guava cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.mount :as mount])
  (:import (clojure.lang ExceptionInfo)
           (com.google.common.cache Cache CacheBuilder Weigher RemovalListener)
           (com.google.common.base Ticker)
           (com.google.common.util.concurrent UncheckedExecutionException)
           (java.util Iterator HashSet)
           (java.util.concurrent ExecutionException ConcurrentHashMap)
           (memento.base EntryMeta)
           (java.util.function BiFunction)))

(defrecord CacheKey [id args])

(def nil-entry (base/->EntryMeta nil false #{}))

(defn process-non-cached
  "Unwrap EntryMeta objects and throw exception to prevent caching if no-cache? is set."
  [obj]
  (if (and (instance? EntryMeta obj)
           (:no-cache? obj))
    (throw (ExceptionInfo. "" {::non-cached obj}))
    obj))

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

(defn val->indexed-cached
  "Converts value into a representation that is suitable for the Cache (no nils),
  and also registers it with the secondary index. Returned value should not be discarded."
  [sec-index k v]
  (sec-index-conj-entry sec-index k (if (nil? v) nil-entry v)))

(defn ^CacheBuilder conf->builder
  "Creates and configures common parameters on the builder."
  [{:memento.core/keys [concurrency initial-capacity size< ttl fade]
    :memento.guava/keys [weight< removal-listener kv-weight weak-keys weak-values
                         soft-values refresh stats ticker]}
   sec-index]
  (let [listener (if removal-listener
                   (reify RemovalListener
                     (onRemoval [this n]
                       (sec-index-disj-entry sec-index (.getKey n) (.getValue n))
                       (removal-listener
                         (.id ^CacheKey (.getKey n))
                         (.args ^CacheKey (.getKey n))
                         (base/unwrap-meta (.getValue n))
                         (.getCause n))))
                   (reify RemovalListener
                     (onRemoval [this n]
                       (sec-index-disj-entry sec-index (.getKey n) (.getValue n)))))]
    (cond-> (.removalListener (CacheBuilder/newBuilder) listener)
      concurrency (.concurrencyLevel concurrency)
      initial-capacity (.initialCapacity initial-capacity)
      weight< (.maximumWeight weight<)
      size< (.maximumSize size<)
      kv-weight (.weigher
                  (reify Weigher (weigh [_this k v]
                                   (kv-weight (.id ^CacheKey k)
                                              (.args ^CacheKey k)
                                              (base/unwrap-meta v)))))
      weak-keys (.weakKeys)
      weak-values (.weakValues)
      soft-values (.softValues)
      ttl (.expireAfterWrite (base/parse-time-scalar ttl) (base/parse-time-unit ttl))
      fade (.expireAfterAccess (base/parse-time-scalar fade) (base/parse-time-unit fade))
      refresh (.refreshAfterWrite (base/parse-time-scalar refresh) (base/parse-time-unit refresh))
      ticker (.ticker (proxy [Ticker] [] (read [] (ticker))))
      stats (.recordStats))))

;;;;;;;;;;;;;;
; ->key-fn take 2 args f and key
(defrecord GCache [conf ^Cache guava-cache ->key-fn ret-fn ^ConcurrentHashMap sec-index]
  base/Cache
  (conf [this] conf)
  (cached [this segment args]
    (base/unwrap-meta
      (let [f (if ret-fn (fn [& args] (ret-fn args (apply (:f segment) args)))
                         (:f segment))
            key (->key-fn segment args)]
        (try
          (.get guava-cache key
                (fn cache-load []
                  (->> args (apply f) process-non-cached (val->indexed-cached sec-index key))))
          (catch ExecutionException e (throw (.getCause e)))
          (catch UncheckedExecutionException e
            (let [cause (.getCause e)
                  data (ex-data cause)]
              (if (contains? data ::non-cached)
                (::non-cached data)
                (throw cause))))))))
  (if-cached [this segment args]
    (if-some [v (.getIfPresent guava-cache (->key-fn segment args))] (base/unwrap-meta v) base/absent))
  (invalidate [this segment]
    (loop [^Iterator it (.. (.asMap guava-cache) keySet iterator)]
      (if (.hasNext it)
        (do (when (= (:id segment) (.id ^CacheKey (.next it)))
              (.remove it))
            (recur it))
        this)))
  (invalidate [this segment args] (.invalidate guava-cache (->key-fn segment args)) this)
  (invalidate-all [this] (.invalidateAll guava-cache) this)
  (invalidate-id [this id]
    (when-let [cache-keys (.remove sec-index id)]
      (.invalidateAll guava-cache cache-keys))
    this)
  (put-all [this segment args-to-vals]
    (reduce-kv #(let [k (->key-fn segment %2)
                      v (val->indexed-cached sec-index k %3)]
                  (.put guava-cache k v))
               nil
               args-to-vals)
    this)
  (as-map [this] (persistent!
                   (reduce (fn [m [k v]]
                             (assoc! m k (base/unwrap-meta v)))
                           (transient {})
                           (.asMap guava-cache))))
  (as-map [this segment]
    (persistent!
      (reduce (fn [m [^CacheKey k v]]
                (if (= (:id segment) (.id k))
                  (assoc! m (.args k) (base/unwrap-meta v))
                  m))
              (transient {})
              (.asMap guava-cache)))))

(defmethod base/new-cache :memento.core/guava [conf]
  (let [sec-index (conf->sec-index conf)]
    (->GCache conf
              (.build (conf->builder conf sec-index))
              (if-let [key-fn (:memento.core/key-fn conf)]
                (fn [segment args] (->CacheKey (:id segment) (key-fn ((:key-fn segment) args))))
                (fn [segment args] (->CacheKey (:id segment) ((:key-fn segment) args))))
              (:memento.core/ret-fn conf)
              sec-index)))

(defn stats
  "Return guava stats for the cache if it is a Guava Cache.

   Takes a memoized fn or a Cache instance as a parameter.

   Returns com.google.common.cache.CacheStats"
  [fn-or-cache]
  (if (satisfies? base/Cache fn-or-cache)
    (when (instance? GCache fn-or-cache)
      (.stats ^Cache (:guava-cache fn-or-cache)))
    (when-let [c (mount/mount-point fn-or-cache)]
      (stats (mount/mounted-cache c)))))

(defn to-data [cache]
  (persistent!
    (reduce (fn [m [k v]]
              (assoc! m [(.id k) (.args k)] (when-not (= v nil-entry) v)))
            (transient {})
            (.asMap (:guava-cache cache)))))

(defn load-data [cache data-map]
  (reduce-kv
    (fn [^Cache c k v]
      (.put c (->CacheKey (first k) (second k)) (if (nil? v) nil-entry v))
      c)
    (:guava-cache cache)
    data-map))
