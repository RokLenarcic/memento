(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.config :as config])
  (:import (java.util.concurrent TimeUnit)))

(def absent "Value that signals absent key." (Object.))

(defrecord EntryMeta [v no-cache? tag-idents])
(defrecord Segment [f key-fn id])

(defn unwrap-meta [o] (if (instance? EntryMeta o) (:v o) o))

(defprotocol Cache
  "Protocol for Cache. It houses entries for multiple functions.

  Args-key is a cache key, presumably created from argument list, and it's used to determine
  'same args' for caching purposes. Usually it's same as args."
  (conf [this] "Return the conf for this cache.")
  (cached [this segment args]
    "Return the cache value.

    - segment is Segment record provided by the mount point, it contains information that allows Cache
    to separate caches for different functions")
  (if-cached [this segment args]
    "Return cached value if present in cache or memento.base/absent otherwise.")
  (invalidate [this segment] [this segment args]
    "Invalidate all the entries linked to a mount or a mount's single arg list, return Cache")
  (invalidate-all [this] "Invalidate all entries, returns Cache")
  (invalidate-id [this id] "Invalidate entries with this secondary ID, returns Cache")
  (put-all [this segment args-to-vals] "Add entries as for a function")
  (as-map [this] [this segment]
    "Return all entries in the cache or all entries in the cache for a mount.
     The first variant returns a map with keys shaped like as per cache implementation, the second returns just some
     sort of a substructure of the first."))

(def no-cache
  (reify Cache
    (conf [this] {config/type config/none})
    (cached [this segment args] (apply (:f segment) args))
    (if-cached [this segment args] absent)
    (invalidate [this segment] this)
    (invalidate [this segment args] this)
    (invalidate-all [this] this)
    (invalidate-id [this id] this)
    (put-all [this f args-to-vals] this)
    (as-map [this] {})
    (as-map [this segment] {})))

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
