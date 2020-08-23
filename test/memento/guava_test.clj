(ns memento.guava-test
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.guava :refer :all]))

(deftest cache-creation
  (testing "Creates a cache builder"
    (are [expected props]
      (= expected (str (spec->builder props false)))
      "CacheBuilder{concurrencyLevel=12}" #::m {:concurrency 12}
      "CacheBuilder{initialCapacity=11}" #::m {:initial-capacity 11}
      "CacheBuilder{maximumSize=29}" #::m {:size< 29}
      "CacheBuilder{maximumWeight=30}" #::m {:weight< 30}
      "CacheBuilder{expireAfterWrite=35000000000ns}" #::m {:ttl 35}
      "CacheBuilder{expireAfterWrite=2100000000000ns}" #::m {:ttl [35 :m]}
      "CacheBuilder{expireAfterAccess=36000000000ns}" #::m {:fade 36}
      "CacheBuilder{expireAfterAccess=36000000ns}" #::m {:fade [36 :ms]}
      "CacheBuilder{}" #::m {:refresh 37}
      "CacheBuilder{}" #::m {:refresh [37 :d]}
      "CacheBuilder{}" #::m {:stats true}
      "CacheBuilder{}" #::m {:kv-weight (fn [k v] 1)}
      "CacheBuilder{keyStrength=weak}" #::m {:weak-keys true}
      "CacheBuilder{valueStrength=weak}" #::m {:weak-values true}
      "CacheBuilder{valueStrength=soft}" #::m {:soft-values true}
      "CacheBuilder{}" #::m {:ticker (fn [] (System/nanoTime))}
      "CacheBuilder{removalListener}" #::m {:removal-listener (fn [k v event] nil)}
      "CacheBuilder{initialCapacity=11, concurrencyLevel=12, maximumWeight=30, expireAfterWrite=100000000000ns, expireAfterAccess=111000000000ns, keyStrength=weak, valueStrength=weak, removalListener}"
      #::m {:concurrency 12
            :initial-capacity 11
            :weight< 30
            :ttl 100
            :fade 111
            :refresh 13
            :stats true
            :weak-keys true
            :weak-values true
            :removal-listener (fn [k v event] nil)}))
  (testing "Creates a working cache"
    (let [a (atom 0)
          builder (spec->builder #::m {:weight< 34
                                       :kv-weight (fn [k v] 20)
                                       :ticker (fn [] (System/nanoTime))
                                       :removal-listener (fn [k v event] nil)}
                                 false)
          cache (.build builder)]
      (is (= 2 (.get cache 1 (fn [] 2)))))))
