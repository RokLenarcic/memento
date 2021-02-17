(ns memento.mount-test
  (:require [memento.mount :as m]
            [clojure.test :refer :all]))

(deftest cache-tags-test
  (testing "Add tags"
    (is (= #:memento.mount-test{:a #{1} :b #{1} :c #{1}}
           (m/assoc-cache-tags {} [::a ::b ::c] 1)))
    (is (= #:memento.mount-test{:a #{2 3 4} :b #{2} :c #{2 3 4} :d #{2}}
           (-> {}
               (m/assoc-cache-tags [::a ::b ::c] 2)
               (m/assoc-cache-tags [::a ::c] 3)
               (m/assoc-cache-tags [::a ::c] 4)
               (m/assoc-cache-tags [::a ::c ::d] 2)))))
  (testing "Remove tags"
    (is (= #:memento.mount-test{:a #{3 4} :b #{} :c #{3 4} :d #{}}
           (-> {}
               (m/assoc-cache-tags [::a ::b ::c] 2)
               (m/assoc-cache-tags [::a ::c] 3)
               (m/assoc-cache-tags [::a ::c] 4)
               (m/assoc-cache-tags [::a ::c ::d] 2)
               (m/dissoc-cache-tags 2))))))

(deftest reify-mount-conf-test
  (is (= {:a 1 :B 3} (m/reify-mount-conf {:a 1 :B 3})))
  (is (= {:memento.core/tags [1]} (m/reify-mount-conf 1)))
  (is (= {:memento.core/tags [1 2 3]} (m/reify-mount-conf [1 2 3]))))

(deftest update-existing-test
  (is (= {:a 1 :b 2} (m/update-existing {:a 1 :b 1} [:b :c] inc))))

(deftest update-tag-caches-test
  (testing "Updates root binding"))
