(ns memento.mount-test
  (:require [memento.mount :as m]
            [memento.base :as b]
            [memento.config :as mc]
            [memento.core :as core]
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

(deftest id-test
  (are [expected-id fn-or-var conf]
    (= expected-id (-> (m/bind fn-or-var conf b/no-cache) .getMp .segment .getId))
    inc inc {}
    inc inc :a
    "#'clojure.core/inc" #'inc {}
    :x #'inc {mc/id :x}
    :x inc {mc/id :x}))

(def a (atom 0))

(defn add-prefix
  [x]
  (swap! a inc)
  (str "prefix-" x))

(defn add-suffix
  [x]
  (swap! a + 10)
  (str (add-prefix x) "-suffix"))

(core/memo #'add-prefix {mc/tags [:test]})
(core/memo #'add-suffix {mc/tags [:test]})

(defn fib
  [x]
  (if (<= x 1) 1 (+ (fib (dec x)) (fib (dec (dec x))))))

(core/memo #'fib {mc/cache {mc/tags [:test]}})

(deftest recursive-call-test
  (testing "Call same cache recursively."
    (let [_ (reset! a 0)
          c (core/create {mc/type mc/caffeine})]
      (is (= "prefix-A-suffix"
             (core/with-caches
               :test (constantly c)
               (add-suffix "A")
               (add-suffix "A")
               (add-suffix "A"))))
      (is (= @a 11))))
  (testing "Call same cache function recursively."
    (let [c (core/create {mc/type mc/caffeine
                          mc/initial-capacity 4})]
      (core/with-caches
        :test (constantly c)
        (is (= 10946 (fib 20)))))))
