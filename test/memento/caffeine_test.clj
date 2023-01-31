(ns memento.caffeine-test
  (:require [clojure.test :refer :all]
            [memento.base :as b]
            [memento.core :as m]
            [memento.config :as mc]
            [memento.caffeine :refer :all]
            [memento.caffeine.config :as mcc])
  (:import (memento.base CacheKey)))

#_(deftest cache-creation
  (testing "Creates a cache builder"
    (are [expected props]
      (= expected (str (conf->builder props nil)))
      "Caffeine{initialCapacity=11, removalListener}" {mc/initial-capacity 11}
      "Caffeine{maximumSize=29, removalListener}" {mc/size< 29}
      "Caffeine{maximumWeight=30, removalListener}" {mcc/weight< 30}
      "Caffeine{expireAfterWrite=35000000000ns, removalListener}" {mc/ttl 35}
      "Caffeine{expireAfterWrite=2100000000000ns, removalListener}" {mc/ttl [35 :m]}
      "Caffeine{expireAfterAccess=36000000000ns, removalListener}" {mc/fade 36}
      "Caffeine{expireAfterAccess=36000000ns, removalListener}" {mc/fade [36 :ms]}
      ;;"Caffeine{removalListener}" {::/refresh 37}
      ;;"Caffeine{removalListener}" {::mg/refresh [37 :d]}
      "Caffeine{removalListener}" {mcc/stats true}
      "Caffeine{removalListener}" {mcc/kv-weight (fn [f k v] 1)}
      "Caffeine{keyStrength=weak, removalListener}" {mcc/weak-keys true}
      "Caffeine{valueStrength=weak, removalListener}" {mcc/weak-values true}
      "Caffeine{valueStrength=soft, removalListener}" {mcc/soft-values true}
      "Caffeine{removalListener}" {mcc/ticker (fn [] (System/nanoTime))}
      "Caffeine{removalListener}" {mcc/removal-listener (fn [k v event] nil)}
      "Caffeine{initialCapacity=11, maximumWeight=30, expireAfterWrite=100000000000ns, expireAfterAccess=111000000000ns, keyStrength=weak, valueStrength=weak, removalListener}"
      {mc/initial-capacity 11
       mcc/weight< 30
       mc/ttl 100
       mc/fade 111
       mcc/stats true
       mcc/weak-keys true
       mcc/weak-values true
       mcc/removal-listener (fn [k v event] nil)}))
  (testing "Creates a working cache"
    (let [a (atom 0)
          builder (conf->builder {mcc/weight< 34
                                  mcc/kv-weight (fn [f k v] 20)
                                  mcc/ticker (fn [] (System/nanoTime))
                                  mcc/removal-listener (fn [f k v event] nil)}
                                 nil)
          cache (.build builder)]
      (is (= 2 (.get cache (->CacheKey identity [1]) (java8function (fn [_] 2))))))))

(deftest data-loading-unloading
  (testing "Serializes"
    (let [c (m/memo identity {mc/type mc/caffeine mc/id "A"})]
      (c 1)
      (is (= (to-data (m/active-cache c)) {["A" '(1)] 1}))))
  (testing "Deserializes"
    (let [c (m/memo identity {mc/type mc/caffeine mc/id "A"})]
      (load-data (m/active-cache c) {["X" '(4)] 5})
      (is (= (b/as-map (m/active-cache c))
             {(CacheKey. "X" [4]) 5})))))
