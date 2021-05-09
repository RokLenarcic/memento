# Namespace scan

You can scan loaded namespaces for annotated vars and automatically create caches.
The scan looks for Vars with `:memento.core/cache` key in the meta.
That value is used as a cache spec.

Given require `[memento.ns-scan :as ns-scan]`:
```clojure
(ns myproject.some-ns
  (:require 
    [myproject.cache :as cache]
    [memento.core :as m]))

; defn already has a nice way for adding meta
(defn test1
  "A function using built-in defn meta mechanism to specify a cache region"
  {::m/cache cache/inf}
  [arg1 arg2]
  (+ arg1 arg2))

; you can also do standard meta syntax
(defn ^{::m/cache cache/inf} test2
  "A function using normal meta syntax to add a cache to itself"
  [arg1 arg2] (+ arg1 arg2))

; this also works on def
(def ^{::m/cache cache/inf} test3 (fn [arg1 arg2] (+ arg1 arg2)))

; attach caches
(ns-scan/attach-caches)
```

This only works on LOADED namespaces, so beware.

Calling `attach-caches` multiple times attaches new caches, replaces existing caches.

Namespaces `clojure.*` and `nrepl.*` are not scanned by default, but you can
provide your own blacklists, see doc.
