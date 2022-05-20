(ns memento.guava
  "Guava cache implementation."
  {:author "Rok Lenarčič"}
  (:require [memento.caffeine :as c]))

(def stats c/stats)

(def to-data c/to-data)

(def load-data c/load-data)
