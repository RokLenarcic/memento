(ns memento.multi-test
  (:require [memento.core :as m]
            [memento.base :as b]
            [memento.config :as mc]
            [clojure.test :refer :all]))

(def inf-cache {mc/type mc/guava})

(defn as-map [cache] (reduce-kv #(assoc %1 (:args %2) %3) {} (b/as-map cache)))

(deftest daisy-test
  (testing ""
    (let [access-count (atom 0)
          upstream-access-count (atom 0)
          c (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! access-count inc) r)))
          up (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! upstream-access-count inc) r)))
          f (m/memo inc (m/daisy c up))
          _ (m/memo-add! f {[2] 3})]
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 2 (f 1)))
      (is (= 3 (f 2)))
      (is (= {[2] 3} (m/as-map f)))
      (is (= {[2] 3} (as-map c)))
      (is (= {[0] 1 [1] 2} (as-map up)))
      (is (= 0 @access-count))
      (is (= 2 @upstream-access-count)))))

(deftest tiered-test
  (testing ""
    (let [access-count (atom 0)
          upstream-access-count (atom 0)
          c (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! access-count inc) r)))
          up (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! upstream-access-count inc) r)))
          f (m/memo inc (m/tiered c up))
          _ (m/memo-add! f {[2] 3})]
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 2 (f 1)))
      (is (= 3 (f 2)))
      (is (= {[0] 1 [1] 2 [2] 3} (m/as-map f)))
      (is (= {[0] 1 [1] 2 [2] 3} (as-map c)))
      (is (= {[0] 1 [1] 2} (as-map up)))
      (is (= 2 @access-count))
      (is (= 2 @upstream-access-count)))))

(deftest consulting-test
  (testing ""
    (let [access-count (atom 0)
          upstream-access-count (atom 0)
          c (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! access-count inc) r)))
          up (m/create (assoc inf-cache mc/ret-fn (fn [_ r] (swap! upstream-access-count inc) r)))
          f (m/memo inc (m/consulting c up))
          _ (m/memo-add! f {[2] 3})]
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 1 (f 0)))
      (is (= 2 (f 1)))
      (is (= 3 (f 2)))
      (is (= {[0] 1 [1] 2 [2] 3} (m/as-map f)))
      (is (= {[0] 1 [1] 2 [2] 3} (as-map c)))
      (is (= {} (as-map up)))
      (is (= 2 @access-count))
      (is (= 0 @upstream-access-count)))))
