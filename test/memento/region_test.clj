(ns memento.region-test
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.config :as mc]
            [memento.region :as region]))

(deftest reg-region
  (testing "that region is created"
    (let [access-count (atom 0)
          region-name (first (remove (set (keys region/*regions*)) (range)))
          region-conf #::m {:region region-name :type ::m/guava :size< 1}
          f (m/memo
              (region/cache region-name)
              (fn [number]
                (swap! access-count inc)))]
      (is (= 1 (f -1)))
      (is (= 2 (f -1)))
      (is (= 3 (f -1)))
      (region/set-region! region-conf)
      (is (= 4 (f -1)))
      (is (= 4 (f -1)))
      (is (= 4 (f -1)))
      (is (= 5 (f 0))))))

(deftest scoped-region
  (testing "that an anonymous scope is created"
    (let [access-count (atom 0)
          f (m/memo
              (region/cache :request-scope)
              (fn [number] (swap! access-count inc)))]
      (is (= 1 (f -1)))
      (is (= 2 (f -1)))
      (is (= 3 (f -1)))
      (region/with-region #::m {:region :request-scope :type ::m/guava}
                   (is (= 4 (f -1)))
                   (is (= 4 (f -1)))
                   (is (= 4 (f -1)))
                   (is (= 5 (f 0))))
      (is (= 6 (f -1)))
      (is (= 7 (f -1)))
      (is (= 8 (f 0)))))
  (testing "that an alias scope is used"
    (let [access-count (atom 0)
          _ (region/set-region! #::m {:region :small-cache
                                      :type ::m/guava
                                      :size< 1})
          f (m/memo
              (region/cache :request-scope)
              (fn [number] (swap! access-count inc)))]
      (is (= 1 (f -1)))
      (is (= 2 (f -1)))
      (is (= 3 (f -1)))
      (region/with-region-alias :request-scope :small-cache
                         (is (= 4 (f -1)))
                         (is (= 4 (f -1)))
                         (is (= 4 (f -1)))
                         (is (= 5 (f 0))))
      (is (= 6 (f -1)))
      (is (= 7 (f -1)))
      (is (= 8 (f 0))))))
