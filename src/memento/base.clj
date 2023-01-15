(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.config :as config])
  (:import (clojure.lang AFn)
           (java.util.concurrent TimeUnit)
           (memento.base EntryMeta ICache)))

(def absent "Value that signals absent key." (Object.))

(defn unwrap-meta [o] (if (instance? EntryMeta o) (.getV ^EntryMeta o) o))

(def no-cache
  (reify ICache
    (conf [this] {config/type config/none})
    (cached [this segment args] (unwrap-meta (AFn/applyToHelper (.getF segment) args)))
    (ifCached [this segment args] absent)
    (invalidate [this segment] this)
    (invalidate [this segment args] this)
    (invalidateAll [this] this)
    (invalidateId [this id] this)
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
(defn invalidate-id [^ICache icache id] (.invalidateId icache id))
(defn put-all [^ICache icache f args-to-vals] (.addEntries icache f args-to-vals))
(defn as-map
  ([^ICache icache] (.asMap icache))
  ([^ICache icache segment] (.asMap icache segment)))

(defmulti new-cache "Instantiate cache. Extension point, do not call directly." config/type)

(defmethod new-cache :memento.core/none [_] no-cache)

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
  (let [unit (if (number? time-param) :s (second time-param))]
    (or (config/timeunits unit)
        (throw (ex-info (str "Unknown cache time unit " unit)
                        {:tu unit})))))

(defn base-create-cache
  "Create a cache.

  A conf is a map of cache settings, see memento.config namespace for names of settings."
  [conf]
  (if (instance? ICache conf)
    conf
    (if config/enabled?
      (new-cache (merge {config/type config/*default-type*} conf))
      no-cache)))
