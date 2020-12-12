(ns memento.ns-scan
  "Scan loaded namespaces for vars that have meta that
  specifies a cache, and attach cache to those vars."
  {:author "Rok Lenarčič"}
  (:require [memento.core :as core]))

(def default-blacklist [#"^clojure\." #"^nrepl\."])

(defn vars
  [black-list]
  (flatten
    (for [n (all-ns)
          :let [ns-str (str (ns-name n))]
          :when (not-any? #(re-find % ns-str) black-list)]
      (or (vals (ns-interns n)) []))))

(defn attach-caches
  "Scans loaded namespaces and attaches new caches to all vars that have
  :memento.core/cache key in meta of the var. Returns coll of affected vars.

  The value of :memento.core/cache meta key is used as conf parameter
  in memento.core/memo.

  Note that ONLY the loaded namespaces are considered.

  You can specify a namespace black-list. It's a list of regexes,
  which are applied to namespace name with re-find (so you only need to match
  part of the name). The value defaults to default-blacklist, which
  blacklists clojure.* and nrepl.*"
  ([]
   (attach-caches default-blacklist))
  ([ns-black-list]
   (doall
     (for [v (vars ns-black-list)
           :let [conf (::core/conf (meta v))]
           :when conf]
       (and (core/memo conf v) v)))))
