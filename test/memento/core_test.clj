(ns memento.core-test
  (:require [clojure.test :refer :all]
            [memento.core :refer :all]))

(def id (memo identity))

(defn- check-core-features
  [factory]
  (let [mine (factory identity)
        them (memoize identity)]
    (testing "That the memo function works the same as core.memoize"
      (are [x y] =
                 (mine 42) (them 42)
                 (mine ()) (them ())
                 (mine []) (them [])
                 (mine #{}) (them #{})
                 (mine {}) (them {})
                 (mine nil) (them nil)))
    (testing "That the memo function has a proper cache"
      (is (memoized? mine))
      (is (not (memoized? them)))
      (is (= 42 (mine 42)))
      (is (not (empty? (into {} (as-map mine)))))
      (is (memo-clear! mine))
      (is (empty? (into {} (as-map mine))))))
  (testing "That the cache retries in case of exceptions"
    (let [access-count (atom 0)
          f (factory
              (fn []
                (swap! access-count inc)
                (throw (Exception.))))]
      (is (thrown? Exception (f)))
      (is (thrown? Exception (f)))
      (is (= 2 @access-count))))
  (testing "That the memo function does not have a race condition"
    (let [access-count (atom 0)
          slow-identity
          (factory (fn [x]
                     (swap! access-count inc)
                     (Thread/sleep 100)
                     x))]
      (every? identity (pvalues (slow-identity 5) (slow-identity 5)))
      (is (= @access-count 1))))
  (testing "That exceptions are correctly unwrapped."
    (is (thrown? ClassNotFoundException ((memo (fn [] (throw (ClassNotFoundException.)))))))
    (is (thrown? IllegalArgumentException ((memo (fn [] (throw (IllegalArgumentException.))))))))
  (testing "Null return caching."
    (let [access-count (atom 0)
          mine (factory (fn [] (swap! access-count inc) nil))]
      (is (nil? (mine)))
      (is (nil? (mine)))
      (is (= @access-count 1)))))

(deftest test-memo
  (check-core-features memo))

(deftest test-lru
  (let [mine (memo {:size< 2} identity)]
    ;; First check that the basic memo behavior holds
    (check-core-features (partial memo {:size< 2}))

    ;; Now check FIFO-specific behavior
    (testing "that when the limit threshold is not breached, the cache works like the basic version"
      (are [x y] =
                 42                 (mine 42)
                 {[42] 42}          (as-map mine)
                 43                 (mine 43)
                 {[42] 42, [43] 43} (as-map mine)
                 42                 (mine 42)
                 {[42] 42, [43] 43} (as-map mine)))
    (testing "that when the limit is breached, the oldest value is dropped"
      (are [x y] =
                 44                 (mine 44)
                 {[44] 44, [43] 43} (as-map mine)))))


(deftest test-ttl
  ;; First check that the basic memo behavior holds
  (check-core-features (partial memo {:ttl 2}))

  ;; Now check TTL-specific behavior
  (let [mine (memo {:ttl [2 :s]})]
    (are [x y] =
               42        (mine 42)
               {[42] 42} (as-map mine))
    (Thread/sleep 3000)
    (are [x y] =
               43        (mine 43)
               {[43] 43} (as-map mine)))

  (let [mine  (memo {:ttl [5 :ms]} identity)
        limit 2000000
        start (System/currentTimeMillis)]
    (loop [n 0]
      (if-not (mine 42)
        (do
          (is false (str  "Failure on call " n)))
        (if (< n limit)
          (recur (+ 1 n)))))
    (println "ttl test completed" limit "calls in"
             (- (System/currentTimeMillis) start) "ms")))

(deftest test-memoization-utils
  (let [CACHE_IDENTITY (:memento.core/cache (meta id))]
    (testing "that the stored cache is not null"
      (is (not= nil CACHE_IDENTITY)))
    (testing "that a populated function looks correct at its inception"
      (is (memoized? id))
      (is (as-map id))
      (is (empty? (as-map id))))
    (testing "that a populated function looks correct after some interactions"
      ;; Memoize once
      (is (= 42 (id 42)))
      ;; Now check to see if it looks right.
      (is (find (as-map id) '(42)))
      (is (= 1 (count (as-map id))))
      ;; Memoize again
      (is (= [] (id [])))
      (is (find (as-map id) '([])))
      (is (= 2 (count (as-map id))))
      (testing "that upon memoizing again, the cache should not change"
        (is (= [] (id [])))
        (is (find (as-map id) '([])))
        (is (= 2 (count (as-map id)))))
      (testing "if clearing the cache works as expected"
        (is (memo-clear! id))
        (is (empty? (as-map id)))))
    (testing "that after all manipulations, the cache maintains its identity"
      (is (identical? CACHE_IDENTITY (:memento.core/cache (meta id)))))
    (testing "that a cache can be seeded and used normally"
      (memo-clear! id)
      (is (memo-add! id {[42] 42}))
      (is (= 42 (id 42)))
      (is (= {[42] 42} (as-map id)))
      (is (= 108 (id 108)))
      (is (= {[42] 42 [108] 108} (as-map id))))
    (testing "that we can get back the original function"
      (is (memo-clear! id))
      (is (memo-add! id {[42] 24}))
      (is (= 24 (id 42)))
      (is (= 42 ((memo-unwrap id) 42))))))

(deftest memo-with-seed-cmemoize-18
  (let [mine (memo {:seed {[42] 99}} identity)]
    (testing "that a memo seed works"
      (is (= 41 (mine 41)))
      (is (= 99 (mine 42)))
      (is (= 43 (mine 43)))
      (is (= {[41] 41, [42] 99, [43] 43} (as-map mine))))))

(deftest memo-with-dropped-args
  ;; must use var to preserve metadata
  (let [mine (memo {:key-fn rest} +)]
    (testing "that key-fnb collapses the cache key space"
      (is (= 13 (mine 1 2 10)))
      (is (= 13 (mine 10 2 1)))
      (is (= 13 (mine 10 2 10)))
      (is (= {[2 10] 13, [2 1] 13} (as-map mine))))))
