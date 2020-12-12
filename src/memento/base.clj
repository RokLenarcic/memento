(ns memento.base
  "Memoization library with many features.

  memento.cache introduces Cache protocol that people need to extend when making
  extensions."
  {:author "Rok Lenarčič"})

(def absent "Value that signals absent key." (Object.))

(defrecord DoNotCache [v])

(defn unwrap-donotcache [o] (loop [v o] (if (instance? DoNotCache v) (recur (:v v)) v)))

(defprotocol Cache
  "Protocol for Cache. If Cache for a Region, it needs to check if Region is started
  to engage in caching."
  (conf [this] "Return the cache conf for this cache.")
  (cached [this args] "Return cached value, possibly invoking the function with the args to
    obtain the value. This should be a thread-safe atomic operation.")
  (uncached [this args]
    "Return the computed value without fetching from the cache, or storing in cache.")
  (if-cached [this args]
    "Return cached value if present in cache or memento.base/absent otherwise.")
  (original-function [this] "Returns the original function")
  (invalidate [this args] "Invalidate entry for args, returns Cache")
  (invalidate-all [this] "Invalidate all entries, returns Cache")
  (put-all [this args-to-vals] "Add entries to cache, returns Cache")
  (as-map [this] "Returns the cache as a map. This does not imply a snapshot,
  as implementation might provide a weakly consistent view of the cache."))

(defprotocol TaggedCache
  "Protocol for Tagged version of the cache. Objects that satisfy this protocol should
  also satisfy Cache."
  (get-tags [this] "Coll of tags for this cache")
  (get-cache-ref [this] "Cache ref for this cache"))
