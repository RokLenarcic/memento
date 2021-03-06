(ns memento.guava-test
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.config :as mc]
            [memento.guava :as mg :refer :all]
            [memento.guava.config :as mcg]))

(deftest cache-creation
  (testing "Creates a cache builder"
    (are [expected props]
      (= expected (str (conf->builder props nil)))
      "CacheBuilder{concurrencyLevel=12, removalListener}" {mc/concurrency 12}
      "CacheBuilder{initialCapacity=11, removalListener}" {mc/initial-capacity 11}
      "CacheBuilder{maximumSize=29, removalListener}" {mc/size< 29}
      "CacheBuilder{maximumWeight=30, removalListener}" {mcg/weight< 30}
      "CacheBuilder{expireAfterWrite=35000000000ns, removalListener}" {mc/ttl 35}
      "CacheBuilder{expireAfterWrite=2100000000000ns, removalListener}" {mc/ttl [35 :m]}
      "CacheBuilder{expireAfterAccess=36000000000ns, removalListener}" {mc/fade 36}
      "CacheBuilder{expireAfterAccess=36000000ns, removalListener}" {mc/fade [36 :ms]}
      "CacheBuilder{removalListener}" {::mg/refresh 37}
      "CacheBuilder{removalListener}" {::mg/refresh [37 :d]}
      "CacheBuilder{removalListener}" {mcg/stats true}
      "CacheBuilder{removalListener}" {mcg/kv-weight (fn [f k v] 1)}
      "CacheBuilder{keyStrength=weak, removalListener}" {mcg/weak-keys true}
      "CacheBuilder{valueStrength=weak, removalListener}" {mcg/weak-values true}
      "CacheBuilder{valueStrength=soft, removalListener}" {mcg/soft-values true}
      "CacheBuilder{removalListener}" {mcg/ticker (fn [] (System/nanoTime))}
      "CacheBuilder{removalListener}" {mcg/removal-listener (fn [k v event] nil)}
      "CacheBuilder{initialCapacity=11, concurrencyLevel=12, maximumWeight=30, expireAfterWrite=100000000000ns, expireAfterAccess=111000000000ns, keyStrength=weak, valueStrength=weak, removalListener}"
      {mc/concurrency 12
       mc/initial-capacity 11
       mcg/weight< 30
       mc/ttl 100
       mc/fade 111
       mcg/stats true
       mcg/weak-keys true
       mcg/weak-values true
       mcg/removal-listener (fn [k v event] nil)}))
  (testing "Creates a working cache"
    (let [a (atom 0)
          builder (conf->builder {mcg/weight< 34
                                  mcg/kv-weight (fn [f k v] 20)
                                  mcg/ticker (fn [] (System/nanoTime))
                                  mcg/removal-listener (fn [f k v event] nil)}
                                 nil)
          cache (.build builder)]
      (is (= 2 (.get cache (->CacheKey identity [1]) (fn [] 2)))))))
