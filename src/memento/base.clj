(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.config :as config])
  (:import (clojure.lang AFn)
           (memento.base CacheEntry EntryMeta ICache)
           (java.util ArrayList)))

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

(defmulti invalidate-secondary!
  "Invalidate secondary identifiers for one cache backend type. Receives start state and
   returns the state passed to end-secondary-invalidation!."
  (fn [cache-type _ids _state] cache-type))

(defmethod invalidate-secondary! :default [_ _ state] state)

(defmulti end-secondary-invalidation!
  "End a secondary-index invalidation for one cache backend type."
  (fn [cache-type _ids _state] cache-type))

(defmethod end-secondary-invalidation! :default [_ _ _] nil)

(defn- record-failure! [^ArrayList failures ^Throwable failure]
  (.add failures failure))

(defn- start-invalidator [^ArrayList failures ids cache-type]
  (try
    {:cache-type cache-type
     :state (volatile! (start-secondary-invalidation! cache-type ids))}
    (catch Throwable t
      (record-failure! failures t)
      nil)))

(defn- run-invalidator! [^ArrayList failures ids {:keys [cache-type state]}]
  (try
    (vreset! state (invalidate-secondary! cache-type ids @state))
    (catch Throwable t
      (record-failure! failures t))))

(defn- end-invalidator! [^ArrayList failures ids {:keys [cache-type state]}]
  (try
    (end-secondary-invalidation! cache-type ids @state)
    (catch Throwable t
      (record-failure! failures t))))

(defn invalidate-secondary-all! [ids]
  (let [failures (ArrayList.)
        cache-types (disj (into (set (keys (methods start-secondary-invalidation!)))
                                (concat (keys (methods invalidate-secondary!))
                                        (keys (methods end-secondary-invalidation!))))
                          :default)
        started (into [] (keep #(start-invalidator failures ids %)) cache-types)]
    (when (.isEmpty failures)
      (run! #(run-invalidator! failures ids %) started))
    (run! #(end-invalidator! failures ids %) started)
    (when-let [^Throwable failure (first failures)]
      (run! #(.addSuppressed failure %) (next failures))
      (throw failure))))

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
