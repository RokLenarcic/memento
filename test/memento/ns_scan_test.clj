(ns memento.ns-scan-test
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.ns-scan :as ns-scan]))

(defn x
  {::m/conf :x}
  [] 1)

(defn y
  {::m/conf {:size< 1}}
  [] 1)

(deftest test-ns-scan
  (testing "should attach a cache"
    (is (= [#'x #'y]
           (ns-scan/attach-caches)))
    (is (= true (m/memoized? x)))
    (is (= true (m/memoized? y))))
  (testing "should attach a new cache"
    (let [temp x
          _ (ns-scan/attach-caches)]
      (is (not= x temp)))))
