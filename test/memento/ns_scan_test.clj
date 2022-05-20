(ns memento.ns-scan-test
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.ns-scan :as ns-scan]))

(defn x
  {::m/cache :x}
  [] 1)

(defn y
  {::m/cache {::m/size< 1 ::m/type ::m/caffeine}}
  [] 1)

(deftest test-ns-scan
  (testing "should attach a cache"
    (is (= [#'x #'y] (ns-scan/attach-caches)))
    (is (= true (m/memoized? x)))
    (is (= true (m/memoized? y))))
  (testing "should attach a new cache"
    (let [temp x
          _ (ns-scan/attach-caches)]
      (is (not= x temp)))))
