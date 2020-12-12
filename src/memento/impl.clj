(ns memento.impl
  "Memoization library with many features.

  This namespace includes internal tooling and shouldn't be used directly
  unless writing extensions."
  {:author "Rok Lenarčič"}
  (:require [memento.base :as base]
            [memento.config :as config]
            [memento.tags :as tags])
  (:import (java.util.concurrent TimeUnit)))

(defmulti new-cache
          "Instantiate cache. Extension point, do not call directly."
          (fn [conf f] (:memento.core/type conf)))

(defrecord NoCache [conf f]
  base/Cache
  (conf [this] conf)
  (cached [this args] (base/uncached this args))
  (uncached [this args] (apply f args))
  (if-cached [this args] base/absent)
  (original-function [this] f)
  (invalidate [this args] this)
  (invalidate-all [this] this)
  (put-all [this _args-to-vals] this)
  (as-map [this] {}))

(defmethod new-cache :memento.core/none
  [conf f]
  (->NoCache conf f))

(defn create-cache
  "Create Cache instance based on conf. Typical constructor based on datastructure."
  [conf f]
  (-> (merge {:memento.core/type config/*default-type*} (meta f) conf)
      (new-cache f)
      tags/tagified-cache))

(defn attach
  "Attach a cache to a fn or var. Internal function.

  Scrape var or fn meta and add it to the conf."
  [fn-or-var conf]
  (if (var? fn-or-var)
    (alter-var-root fn-or-var attach (merge (meta fn-or-var) conf))
    (let [cache (create-cache conf fn-or-var)
          get-fn (if config/enabled? base/cached base/uncached)]
      (with-meta
        (fn [& args] (base/unwrap-donotcache (get-fn cache args)))
        {::cache cache}))))

(defn active-cache
  "Return active cache from the object's meta."
  [obj] (::cache (meta obj)))

(defn prepare-fn
  "Preprocesses the invoke fn, by combining f and ret-fn head of time."
  [f ret-fn]
  (cond->> f ret-fn (comp ret-fn)))

(defn prepare-key-fn
  "Preprocesses key fn."
  [key-fn]
  (if key-fn (fnil key-fn '()) (fn [k] (if (nil? k) '() k))))

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
  (config/timeunits (if (number? time-param) :s (second time-param))))
