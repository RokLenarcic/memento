(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.config :as config])
  (:import (clojure.lang AFn)
            (memento.base CacheEntry EntryMeta ICache)
            (java.util ArrayList)
            (java.util.concurrent.atomic AtomicBoolean)))

(def absent "Value that signals absent key." EntryMeta/absent)

(defn unwrap-meta [o] (CacheEntry/unwrap o))

(def no-cache
  (reify ICache
    (conf [this] {config/type config/none})
    (cached [this segment args] (unwrap-meta (AFn/applyToHelper (.getF segment) args)))
    (ifCached [this segment args] absent)
    (invalidate [this segment] this)
    (invalidate [this segment args] this)
    (invalidateAll [this] this)
    (addEntries [this f args-to-vals] this)
    (asMap [this] {})
    (asMap [this segment] {})))

(defn conf [^ICache icache] (.conf icache))
(defn cached [^ICache icache segment args] (.cached icache segment args))
(defn if-cached [^ICache icache segment args] (.ifCached icache segment args))
(defn invalidate
  ([^ICache icache segment args] (.invalidate icache segment args))
  ([^ICache icache segment] (.invalidate icache segment)))
(defn invalidate-all [^ICache icache] (.invalidateAll icache))
(defn put-all [^ICache icache f args-to-vals] (.addEntries icache f args-to-vals))
(defn as-map
  ([^ICache icache] (.asMap icache))
  ([^ICache icache segment] (.asMap icache segment)))

(defmulti new-cache "Instantiate cache. Extension point, do not call directly." config/type)

(defmulti start-secondary-invalidation!
  "Begin a secondary-index invalidation for one cache backend type and return
   implementation-specific state. All backends are started before any invalidate call."
  (fn [cache-type _ids] cache-type))

(defmethod start-secondary-invalidation! :default [_ _] nil)

(defmulti finalize-invalidation!
  "Finalize a secondary-index invalidation for one cache backend type. Receives
   start state and whether matching entries should be invalidated."
  (fn [cache-type _ids _state _invalidate?] cache-type))

(defmethod finalize-invalidation! :default [_ _ _ _] nil)

(defn- record-failure! [^ArrayList failures ^Throwable failure]
  (.add failures failure))

(defn- throw-failures!
  "Throw the first recorded failure with the rest attached as suppressed exceptions.
   Returns nil when nothing failed."
  [^ArrayList failures]
  (when-let [^Throwable failure (first failures)]
    (run! (fn [^Throwable other]
            ;; addSuppressed rejects self-suppression, and a backend may well
            ;; propagate the same instance twice.
            (when-not (identical? failure other)
              (.addSuppressed failure other)))
          (rest failures))
    (throw failure)))

(defn- start-invalidator [^ArrayList failures sec-ids cache-type]
  (try
    {:cache-type cache-type
     :state (start-secondary-invalidation! cache-type sec-ids)}
    (catch Throwable t
      (record-failure! failures t)
      nil)))

(defn- finalize-invalidator! [^ArrayList failures sec-ids {:keys [cache-type state]} invalidate?]
  (try
    (finalize-invalidation! cache-type sec-ids state invalidate?)
    (catch Throwable t
      (record-failure! failures t))))

(defn start-secondary-invalidation-all!
  "Start a secondary-index invalidation on every registered cache backend type.

   Returns a single-use function of one boolean: true finalizes the invalidation and
   ends the lockout, false ends the lockout without invalidating. See
   memento.core/start-invalidation! for the caller-facing contract."
  [sec-ids]
  (let [failures (ArrayList.)
        cache-types (disj (into (set (keys (methods start-secondary-invalidation!)))
                                (keys (methods finalize-invalidation!)))
                          :default)
        started (into [] (keep #(start-invalidator failures sec-ids %)) cache-types)
        completed (AtomicBoolean.)]
    (when (seq failures)
      ;; Do not leave partially started backends locked out.
      (run! #(finalize-invalidator! failures sec-ids % false) started)
      (throw-failures! failures))
    (fn [invalidate?]
      (when-not (.compareAndSet completed false true)
        (throw (IllegalStateException. "Invalidation has already completed")))
      (run! #(finalize-invalidator! failures sec-ids % invalidate?) started)
      (throw-failures! failures))))

(defmethod new-cache :memento.core/none [_] no-cache)

(defn base-create-cache
  "Create a cache.

  A conf is a map of cache settings, see memento.config namespace for names of settings."
  [conf]
  (if (instance? ICache conf)
    conf
    (cond
      (config/cache conf) (throw (ex-info "You are passing meta key :memento.core/cache into cache constructor. Did you mean to specify :memento.core/type?" {}))
      config/enabled? (new-cache (merge {config/type config/*default-type*} conf))
      :else no-cache)))
