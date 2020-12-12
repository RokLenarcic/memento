(ns memento.tags-test
  (:require [clojure.test :refer :all]
            [memento.tags :as t]))

(deftest cache-tags-test
  (testing "Add tags"
    (is (= #:memento.tags-test{:a #{1} :b #{1} :c #{1}}
           (t/assoc-cache-tags {} [::a ::b ::c] 1)))
    (is (= #:memento.tags-test{:a #{2 3 4} :b #{2} :c #{2 3 4} :d #{2}}
           (-> {}
               (t/assoc-cache-tags [::a ::b ::c] 2)
               (t/assoc-cache-tags [::a ::c] 3)
               (t/assoc-cache-tags [::a ::c] 4)
               (t/assoc-cache-tags [::a ::c ::d] 2)))))
  (testing "Remove tags"
    (is (= #:memento.tags-test{:a #{3 4} :b #{} :c #{3 4} :d #{}}
           (-> {}
               (t/assoc-cache-tags [::a ::b ::c] 2)
               (t/assoc-cache-tags [::a ::c] 3)
               (t/assoc-cache-tags [::a ::c] 4)
               (t/assoc-cache-tags [::a ::c ::d] 2)
               (t/dissoc-cache-tags 2))))))
