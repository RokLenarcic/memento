(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.config :as config])
  (:import (clojure.lang AFn)
           (memento.base EntryMeta ICache LockoutMap)))

(def absent "Value that signals absent key." EntryMeta/absent)

(defn unwrap-meta [o] (if (instance? EntryMeta o) (.getV ^EntryMeta o) o))

(def ^LockoutMap lockout-map
  "A LockoutMap. Implementation developers use this to do caching in a fashion that is aware
   of bulk invalidation. "
  LockoutMap/INSTANCE)

(def no-cache
  (reify ICache
    (conf [this] {config/type config/none})
    (cached [this segment args] (unwrap-meta (AFn/applyToHelper (.getF segment) args)))
    (ifCached [this segment args] absent)
    (invalidate [this segment] this)
    (invalidate [this segment args] this)
    (invalidateAll [this] this)
    (invalidateIds [this ids] this)
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
(defn invalidate-ids [^ICache icache ids] (.invalidateIds icache ids))
(defn put-all [^ICache icache f args-to-vals] (.addEntries icache f args-to-vals))
(defn as-map
  ([^ICache icache] (.asMap icache))
  ([^ICache icache segment] (.asMap icache segment)))

(defmulti new-cache "Instantiate cache. Extension point, do not call directly." config/type)

(defmethod new-cache :memento.core/none [_] no-cache)

(defn base-create-cache
  "Create a cache.

  A conf is a map of cache settings, see memento.config namespace for names of settings."
  [conf]
  (if (instance? ICache conf)
    conf
    (if config/enabled?
      (new-cache (merge {config/type config/*default-type*} conf))
      no-cache)))
