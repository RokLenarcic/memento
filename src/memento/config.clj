(ns memento.config
  "Memoization library config.

  Contains global settings that manipulate the cache mechanisms.
  See doc strings.

  Also contains documented definitios of standard options of cache config."
  {:author "Rok Lenarčič"}
  (:import (java.util.concurrent TimeUnit)))

(def enabled?
  "If false, then all cache attach operations create a cache that does no
  caching (changing this value doesn't affect caches already created).

  Initially has the value of java property `memento.enabled` (defaulting to true)."
  (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def ^:dynamic *default-type* "Default cache type." :memento.core/none)

(def type
  "Type of cache or region that will be instantiated, a keyword.

   The library has two built-ins:
  - memento.core/none
  - memento.core/guava

  If not specified the caches created default to *default-type*."
  :memento.core/type)

(def key-fn
  "A function to be used to calculate the cache key (fn [f-args] key).

  Cache key affects what is considered the 'same' argument list for a function and it will affect caching in that manner.


  It's a function of 1 argument, the seq of function arguments. If not provided it defaults to identity."
  :memento.core/key-fn)

(def ret-fn
  "A function that is ran to process the return of the function, before it's memoized,
   (fn [value] transformed-value).

  It can provide some generic transformation facility, but more importantly, it can wrap specific return
  values in 'do-not-cache' object, that prevents caching."
  :memento.core/ret-fn)

(def seed
  "A map of cache keys to values that will be preloaded when cache is created."
  :memento.core/seed)

(def region
  "Identifier of the region. Used by cache region configuration and the configuration
  of region Cache (which uses the regions) "
  :memento.core/region)

(def guava
  "Type name of guava cache implementation"
  :memento.core/guava)

(def none
  "Type name of noop cache implementation"
  :memento.core/none)

(def regional
  "Type of the regional cache implementation"
  :memento.core/regional)

(def concurrency
  "Supported by: guava, an int.

  Guides the allowed concurrency among update operations.  The table is internally partitioned to try to permit
  the indicated number of concurrent updates without contention"
  :memento.core/concurrency)

(def initial-capacity
  "Supported by: guava, an int.

  Sets the minimum total size for the internal hash tables. Providing a large enough estimate
  at construction time avoids the need for expensive resizing operations later,
  but setting this value unnecessarily high wastes memory."
  :memento.core/initial-capacity)

(def size<
  "Supported by: guava, a long.

  Specifies the maximum number of entries the cache may contain. Some implementations might evict entries
  even before the number of entries reaches the limit."
  :memento.core/size<)

(def ttl
  "Supported by: guava, a duration.

  Specifies that each entry should be automatically removed from the cache once a fixed duration
  has elapsed after the entry's creation, or the most recent replacement of its value via a put.

  Duration is specified by a number (of seconds) or a vector of a number and unit keyword.
  So ttl of 10 or [10 :s] is the same. See 'timeunits' var."
  :memento.core/ttl)

(def fade
  "Supported by: guava, a duration.

  Specifies that each entry should be automatically removed from the cache once a fixed duration
  has elapsed after the entry's creation, the most recent replacement of its value, or its last access.
  Access time is reset by all cache read and write operations.

  Duration is specified by a number (of seconds) or a vector of a number and unit keyword.
  So fade of 10 or [10 :s] is the same. See 'timeunits' var."
  :memento.core/fade)

(def timeunits
  "Timeunits keywords"
  {:ns TimeUnit/NANOSECONDS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :s TimeUnit/SECONDS
   :m TimeUnit/MINUTES
   :h TimeUnit/HOURS
   :d TimeUnit/DAYS})
