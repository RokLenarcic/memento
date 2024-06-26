(ns memento.config
  "Memoization library config.

  Contains global settings that manipulate the cache mechanisms.
  See doc strings.

  Contains conf settings as vars.

  Also contains documented definitions of standard options of cache config."
  {:author "Rok Lenarčič"}
  (:refer-clojure :exclude [type])
  (:import (java.util.concurrent TimeUnit)))

(def ^:redef enabled?
  "If false, then all cache attach operations create a cache that does no
  caching (changing this value doesn't affect caches already created).

  Initially has the value of java property `memento.enabled` (defaulting to true)."
  (Boolean/valueOf (System/getProperty "memento.enabled" "true")))

(def ^:redef reload-guards?
  "If true, then whenever a function cached with tags is garbage collected (e.g. after a namespace reload in REPL),
  a cleanup is done of global tags map. Can be turned off if you don't intend to reload namespaces or do other
  actions that would GC cached function instances.

  Initially has the value of java property `memento.reloadable` (defaulting to true)."
  (Boolean/valueOf (System/getProperty "memento.reloadable" "true")))

(def ^:dynamic *default-type* "Default cache type." :memento.core/none)

(def type
  "Cache setting, type of cache or region that will be instantiated, a keyword.

   The library has two built-ins:
  - memento.core/none
  - memento.core/caffeine

  If not specified the caches created default to *default-type*."
  :memento.core/type)

(def bind-mode
  "Function bind setting, defaults to :new. It governs what the bind will do if you try to bind
  a cache to a function that is already cached, e.g. what happens when memo is called multiple times
  on same Var. Options are:
  - :keep, keeps old cache binding
  - :new, keeps the new cache binding
  - :stack, stacks the caches, so the new binding wraps the older cached function"
  :memento.core/bind-mode)

(def key-fn
  "Cache and function bind setting, a function to be used to calculate the cache key (fn [f-args] key).

  Cache key affects what is considered the 'same' argument list for a function and it will affect caching in that manner.

  If both function bind and cache have this setting, then function bind key-fn is applied first.

  It's a function of 1 argument, the seq of function arguments. If not provided it defaults to identity."
  :memento.core/key-fn)

(def key-fn*
  "Function bind setting, works same as key-fn but the provided function will receive all
  arguments that the original function does. If both key-fn and key-fn* are provided, key-fn is used."
  :memento.core/key-fn*)

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

(def ^:deprecated guava
  "DEPRECATED: Cache setting value, now points to caffeine implementation"
  :memento.core/caffeine)

(def caffeine
  "Cache setting value, type name of Caffeine cache implementation"
  :memento.core/caffeine)

(def none
  "Cache setting value, type name of noop cache implementation"
  :memento.core/none)

(def ^:deprecated concurrency
  "DEPRECATED: it does nothing in Caffeine implementation"
  :memento.core/concurrency)

(def initial-capacity
  "Cache setting, supported by: caffeine, an int.

  Sets the minimum total size for the internal hash tables. Providing a large enough estimate
  at construction time avoids the need for expensive resizing operations later,
  but setting this value unnecessarily high wastes memory."
  :memento.core/initial-capacity)

(def size<
  "Cache setting, supported by: caffeine, a long.

  Specifies the maximum number of entries the cache may contain. Some implementations might evict entries
  even before the number of entries reaches the limit."
  :memento.core/size<)

(def ttl
  "Cache and Function bind setting, a duration.

  Specifies that each entry should be automatically removed from the cache once a duration
  has elapsed after the entry's creation, or the most recent replacement of its value via a put.

  Duration is specified by a number (of seconds) or a vector of a number and unit keyword.
  So ttl of 10 or [10 :s] is the same. See 'timeunits' var."
  :memento.core/ttl)

(def fade
  "Cache and Function bind setting, a duration.

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

(def id
  "Function bind setting.

  Id of the function bind. If you're memoizing a Var, this defaults to stringified var name,
  otherwise the ID is the function itself.

  This is useful to specify when you're using Cache implementation that stores data outside JVM,
  as they often need a name for each function's cache."
  :memento.core/id)

(def timeunits
  "Timeunits keywords, corresponds with Durations class."
  {:ns TimeUnit/NANOSECONDS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :s TimeUnit/SECONDS
   :m TimeUnit/MINUTES
   :h TimeUnit/HOURS
   :d TimeUnit/DAYS})

(def cache
  "The key extracted from object/var meta and used as cache configuration when
  1-arg memo is called or ns-scan based mounting is performed."
  :memento.core/cache)

(def mount
  "The key extracted from object/var meta and used as mount configuration when
  1-arg memo is called or ns-scan based mounting is performed."
  :memento.core/mount)

(def ret-ex-fn
  "Cache and function bind setting, a function that is ran to process the throwable thrown by the function,
   (fn [fn-args throwable] throwable)."
  :memento.core/ret-ex-fn)
