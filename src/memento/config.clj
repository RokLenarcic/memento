(ns memento.config
  "Memoization library config.

  Contains global settings that manipulate the cache mechanisms.
  See doc strings.

  Contains conf settings as vars.

  Also contains documented definitions of standard options of cache config."
  {:author "Rok Lenarčič"}
  (:refer-clojure :exclude [type])
  (:import (java.util.concurrent TimeUnit)))

(def enabled?
  "If false, then all cache attach operations create a cache that does no
  caching (changing this value doesn't affect caches already created).

  Initially has the value of java property `memento.enabled` (defaulting to true)."
  (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def reload-guards?
  "If false, then all cache attach operations create a cache that does no
  caching (changing this value doesn't affect caches already created).

  Initially has the value of java property `memento.enabled` (defaulting to true)."
  (Boolean/valueOf (System/getProperty "memento.reloadable" "true")))

(def ^:dynamic *default-type* "Default cache type." :memento.core/none)

(def type
  "Cache setting, type of cache or region that will be instantiated, a keyword.

   The library has two built-ins:
  - memento.core/none
  - memento.core/guava

  If not specified the caches created default to *default-type*."
  :memento.core/type)

(def key-fn
  "Cache and function bind setting, a function to be used to calculate the cache key (fn [f-args] key).

  Cache key affects what is considered the 'same' argument list for a function and it will affect caching in that manner.

  It's a function of 1 argument, the seq of function arguments. If not provided it defaults to identity."
  :memento.core/key-fn)

(def ret-fn
  "Cache and function bind setting, a function that is ran to process the return of the function, before it's memoized,
   (fn [fn-args ret-value] transformed-value).

  It can provide some generic transformation facility, but more importantly, it can wrap specific return
  values in 'do-not-cache' object, that prevents caching or wrap with tagged IDs."
  :memento.core/ret-fn)

(def evt-fn
  "Function bind setting, a function that is invoked when any event is fired at the function.

   (fn [mnt-point event] void)

   Useful generally to push data into the related cache, the mnt-point parameter implement MountPoint protocol
   so you can invoke memo-add! and such on it. The event can be any data, it's probably best to come up with
   a format that enables the functions that receive the event to be able to tell them apart."
  :memento.core/evt-fn)

(def seed
  "Function bind setting, a map of cache keys to values that will be preloaded when cache is bound."
  :memento.core/seed)

(def guava
  "Cache setting value, type name of guava cache implementation"
  :memento.core/guava)

(def none
  "Cache setting value, type name of noop cache implementation"
  :memento.core/none)

(def concurrency
  "Cache setting, supported by: guava, an int.

  Guides the allowed concurrency among update operations.  The table is internally partitioned to try to permit
  the indicated number of concurrent updates without contention"
  :memento.core/concurrency)

(def initial-capacity
  "Cache setting, supported by: guava, an int.

  Sets the minimum total size for the internal hash tables. Providing a large enough estimate
  at construction time avoids the need for expensive resizing operations later,
  but setting this value unnecessarily high wastes memory."
  :memento.core/initial-capacity)

(def size<
  "Cache setting, supported by: guava, a long.

  Specifies the maximum number of entries the cache may contain. Some implementations might evict entries
  even before the number of entries reaches the limit."
  :memento.core/size<)

(def ttl
  "Cache setting, supported by: guava, a duration.

  Specifies that each entry should be automatically removed from the cache once a fixed duration
  has elapsed after the entry's creation, or the most recent replacement of its value via a put.

  Duration is specified by a number (of seconds) or a vector of a number and unit keyword.
  So ttl of 10 or [10 :s] is the same. See 'timeunits' var."
  :memento.core/ttl)

(def fade
  "Cache setting, supported by: guava, a duration.

  Specifies that each entry should be automatically removed from the cache once a fixed duration
  has elapsed after the entry's creation, the most recent replacement of its value, or its last access.
  Access time is reset by all cache read and write operations.

  Duration is specified by a number (of seconds) or a vector of a number and unit keyword.
  So fade of 10 or [10 :s] is the same. See 'timeunits' var."
  :memento.core/fade)

(def tags
  "Function bind setting.

  List of tags for this memoized bind."
  :memento.core/tags)

(def timeunits
  "Timeunits keywords"
  {:ns TimeUnit/NANOSECONDS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :s TimeUnit/SECONDS
   :m TimeUnit/MINUTES
   :h TimeUnit/HOURS
   :d TimeUnit/DAYS})
