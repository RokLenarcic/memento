(ns memento.core-test
  (:require [clojure.test :refer :all]
            [memento.core :as m :refer :all]
            [memento.config :as mc]
            [memento.caffeine.config :as mcc]
            )
  (:import (java.io IOException)
           (memento.base EntryMeta ICache)
           (memento.caffeine Expiry)
           (memento.mount IMountPoint)))

(def inf {mc/type mc/caffeine})
(defn size< [max-size]
  (assoc inf mc/size< max-size))
(defn ret-fn [f]
  (assoc inf mc/ret-fn f))

(def id (memo identity inf))

(defn- check-core-features
  [factory]
  (let [mine (factory identity)
        them (memoize identity)]
    (testing "That the memo function works the same as core.memoize"
      (are [x y] (= x y)
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
                (throw (IllegalArgumentException.))))]
      (is (thrown? IllegalArgumentException (f)))
      (is (thrown? IllegalArgumentException (f)))
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
    (is (thrown? ClassNotFoundException ((factory (fn [] (throw (ClassNotFoundException.)))))))
    (is (thrown? IllegalArgumentException ((factory (fn [] (throw (IllegalArgumentException.))))))))
  (testing "Null return caching."
    (let [access-count (atom 0)
          mine (factory (fn [] (swap! access-count inc) nil))]
      (is (nil? (mine)))
      (is (nil? (mine)))
      (is (= @access-count 1)))))

(deftest test-memo (check-core-features #(memo % inf)))

(deftest test-lru
  (let [mine (memo identity (size< 2))]
    ;; First check that the basic memo behavior holds
    (check-core-features #(memo % (size< 2)))

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
  (check-core-features #(memo % (assoc inf mc/ttl 2)))

  ;; Now check TTL-specific behavior
  (let [mine (memo identity (assoc inf mc/ttl [2 :s]))]
    (are [x y] =
               42        (mine 42)
               {[42] 42} (as-map mine))
    (Thread/sleep 3000)
    (are [x y] =
               43        (mine 43)
               {[43] 43} (as-map mine)))

  (let [mine  (memo identity (assoc inf mc/ttl [5 :ms]))
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
  (let [CACHE_IDENTITY (:memento.mount/mount (meta id))]
    (testing "that the stored cache is not null"
      (is (instance? IMountPoint id)))
    (testing "that a populated function looks correct at its inception"
      (is (memoized? id))
      (is (instance? ICache (active-cache id)))
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
      (is (identical? CACHE_IDENTITY (:memento.mount/mount (meta id)))))
    (testing "that a cache can be seeded and used normally"
      (memo-clear! id)
      (is (memo-add! id {[42] 42}))
      (is (= 42 (id 42)))
      (is (= {[42] 42} (as-map id)))
      (is (= 108 (id 108)))
      (is (= {[42] 42 [108] 108} (as-map id)))
      (is (memo-add! id {[111] nil [nil] 111}))
      (is (= 111 (id nil)))
      (is (= nil (id 111)))
      (is (= {[42] 42 [108] 108 [111] nil [nil] 111} (as-map id))))
    (testing "that we can get back the original function"
      (is (memo-clear! id))
      (is (memo-add! id {[42] 24}))
      (is (= 24 (id 42)))
      (is (= 42 ((memo-unwrap id) 42))))))

(deftest memo-with-seed-cmemoize-18
  (let [mine (memo identity (assoc inf mc/seed {[42] 99}))]
    (testing "that a memo seed works"
      (is (= 41 (mine 41)))
      (is (= 99 (mine 42)))
      (is (= 43 (mine 43)))
      (is (= {[41] 41, [42] 99, [43] 43} (as-map mine))))))

(deftest memo-with-dropped-args
  ;; must use var to preserve metadata
  (let [mine (memo + (assoc inf mc/key-fn rest))]
    (testing "that key-fnb collapses the cache key space"
      (is (= 13 (mine 1 2 10)))
      (is (= 13 (mine 10 2 1)))
      (is (= 13 (mine 10 2 10)))
      (is (= {[2 10] 13, [2 1] 13} (as-map mine))))))

(def test-atom (atom 0))
(defn test-var-fn [x] (swap! test-atom inc) (* x 3))

(deftest add-memo-to-var
  (testing "that memoing a var works"
    (memo #'test-var-fn inf)
    (is (= 3 (test-var-fn 1)))
    (is (= 3 (test-var-fn 1)))
    (is (= 3 (test-var-fn 1)))
    (is (= @test-atom 1))))

(deftest seed-test
  (testing "that seeding a function works"
    (let [cached (memo + (assoc inf mc/seed {[3 5] 100 [4 5] 2000}))]
      (is (= 50 (cached 20 30)))
      (is (= 1 (cached -1 2)))
      (is (= 100 (cached 3 5)))
      (is (= 2000 (cached 4 5))))))

(deftest key-fn-test
  (testing "that key-fn works for direct cache"
    (let [cached (memo (fn [& ids] ids) (assoc inf mc/key-fn set))]
      (is (= [3 2 1] (cached 3 2 1)))
      (is (= [3 2 1] (cached 1 2 3)))
      (is (= [3 2 1] (cached 1 3 3 2 2 2)))
      (is (= [2 1] (cached 2 1))))))

(deftest key-fn*-test
  (testing "that key-fn works for direct cache"
    (let [cached (memo (fn [& ids] ids) (assoc inf mc/key-fn* hash-set))]
      (is (= [3 2 1] (cached 3 2 1)))
      (is (= [3 2 1] (cached 1 2 3)))
      (is (= [3 2 1] (cached 1 3 3 2 2 2)))
      (is (= [2 1] (cached 2 1))))))

(deftest ret-fn-non-cached
  (testing "that ret-fn is ran"
    (is (= -4 ((memo + (ret-fn #(* -1 %2))) 2 2)))
    (is (= true ((memo (constantly nil) (ret-fn #(nil? %2))) 1)))
    (is (= nil ((memo + (ret-fn (constantly nil))) 2 2))))
  (testing "that non-cached is respected"
    (let [access-nums (atom [])
          f (memo
              (fn [number]
                (swap! access-nums conj number)
                (if (zero? (mod number 3)) (do-not-cache number) number))
              (ret-fn #(if (and (number? %2) (zero? (mod %2 5))) (do-not-cache %2) %2)))]
      (is (= (range 20) (map f (range 20))))
      (is (= (range 20) (map f (range 20))))
      (is (= (concat (range 20) [0 3 5 6 9 10 12 15 18]) @access-nums)))))

(deftest get-tags-test
  (testing "tags get returned"
    (let [cached (memo identity :person)
          cached2 (memo identity [:actor :dog])
          cached3 (memo identity {mc/tags :x})]
      (is (= [:person] (tags cached)))
      (is (= [:actor :dog] (tags cached2)))
      (is (= [:x] (tags cached3))))))

(deftest with-caches-test
  (testing "a different cache is used within the block"
    (let [access-nums (atom [])
          f (memo (fn [number] (swap! access-nums conj number)) :person inf)]
      (is (= [10] (f 10)))
      (is (= [10] (f 10)))
      (is (= [10 20] (f 20)))
      (is (= [10 20] (f 20)))
      (is (= [10 20] @access-nums))
      (with-caches :person (constantly (create inf))
        (is (= [10 20 10] (f 10)))
        (is (= [10 20 10] (f 10)))
        (is (= [10 20 10 30] (f 30)))
        (is (= [10 20 10 30] @access-nums)))
      (is (= [10] (f 10)))
      (is (= [10 20 10 30 30] (f 30))))))

(deftest update-tag-caches-test
  (testing "changes cache root binding"
    (let [access-nums (atom 0)
          f (memo (fn [number] (swap! access-nums + number)) :person inf)]
      (is (= 10 (f 10)))
      (is (= 10 (f 10)))
      (is (= 10 @access-nums))
      (update-tag-caches! :person (constantly (create inf)))
      (is (= 20 (f 10)))
      (is (= 20 @access-nums))
      (with-caches :person (constantly (create inf))
        (is (= 30 (f 10)))
        (is (= 30 (f 10)))
        (is (= 30 @access-nums))
        (update-tag-caches! :person (constantly (create inf)))
        (is (= 40 (f 10)))
        (is (= 40 @access-nums)))
      (is (= 20 (f 10)))
      (is (= 40 @access-nums))
      (update-tag-caches! :person (constantly (create inf)))
      (is (= 50 (f 10)))
      (is (= 50 @access-nums)))))

(deftest tagged-eviction-test
  (testing "adding tag ID info"
    (is (= (EntryMeta. 1 false #{[:person 55]})
           (-> 1 (with-tag-id :person 55))))
    (is (= (EntryMeta. 1 true #{[:person 55] [:account 6]})
           (-> 1 (with-tag-id :person 55) (with-tag-id :account 6) do-not-cache))))
  (testing "tagged eviction"
    (let [f (memo (fn [x] (with-tag-id x :tag x)) :tag inf)]
      (is (= {} (as-map f)))
      (is (= {[1] 1} (do (f 1) (as-map f))))
      (is (= {[1] 1 [2] 2} (do (f 2) (as-map f))))
      (is (= {[2] 2} (do (memo-clear-tag! :tag 1) (as-map f)))))))

(deftest fire-event-test
  (testing "event is fired on referenced cache"
    (let [access-nums (atom 0)
          inner-f (fn [x] (swap! access-nums inc) x)
          evt-f (fn [this evt]
                  (m/memo-add! this {[evt] (inc evt)}))
          x (m/memo inner-f {mc/type mc/caffeine mc/evt-fn evt-f mc/tags [:a]})
          y (m/memo inner-f {mc/type mc/caffeine mc/evt-fn evt-f})]
      (is (= 1 (x 1)))
      (is (= 1 (x 1)))
      (is (= 1 @access-nums))
      (m/fire-event! x 4)
      (m/fire-event! :a 5)
      (m/fire-event! y 6)
      (is (= {[1] 1
              [4] 5
              [5] 6} (m/as-map x)))
      (is (= {[6] 7} (m/as-map y)))
      (is (= 5 (x 4)))
      (is (= 6 (x 5)))
      (is (= 7 (y 6)))
      (is (= 1 @access-nums)))))

(deftest if-cached-test
  (testing "if-cached executes then when cached"
    (let [x (m/memo identity {mc/type mc/caffeine})]
      (x 2)
      (is (= 2
             (m/if-cached [y (x 2)]
               y
               (throw (ex-info "Shouldn't throw" {})))))))
  (testing "if-cached executes else when not cached"
    (let [x (m/memo identity {mc/type mc/caffeine})]
      (is (= ::none
             (m/if-cached [y (x 2)]
               (throw (ex-info "Shouldn't throw" {}))
               ::none))))))

(deftest put-during-load-test
  (testing "adding entries during load"
    (let [c (m/create inf)
          fn1 (m/memo identity {} c)
          fn2 (m/memo (fn [x] (m/memo-add! fn1 {[x] (inc x)})
                        (dec x)))]
      (is (= 4 (fn2 5)))
      (is (= 6 (fn1 5))))))

(defn fib [x] (if (<= x 1) 1 (+ (fib (- x 2)) (fib (dec x)))))

(memo #'fib inf)

(defn recursive [x] (recursive x))

(memo #'recursive inf)

(deftest recursive-test
  (testing "recursive loads"
    (is (= 20365011074 (fib 50)))
    (is (thrown? StackOverflowError (recursive 1)))))

(deftest concurrent-load
  (testing "concurrent test"
    (let [cnt (atom 0)
          f (m/memo (fn [x]
                      (Thread/sleep 1000)
                      (swap! cnt inc) x)
                    inf)
          v (doall (repeatedly 5 #(future (f 1))))]
      (is (= [1 1 1 1 1] (mapv deref v))))))

(deftest vectors-key-fn*
  (testing "vectors don't throw exception when used with key-fn*"
    (let [c (m/memo identity (assoc inf mc/key-fn* identity))]
      (is (some? (m/memo-add! c {[1] 2}))))))

(deftest invalidation-during-load-test
  (testing "bulk invalidation test"
    (let [a (atom 0)
          c (m/memo (fn [] (Thread/sleep 1000)
                      (m/with-tag-id (swap! a inc) :xx 1))
                    (assoc inf mc/tags :xx))]
      (future (Thread/sleep 100)
              (m/memo-clear-tag! :xx 1))
      (is (= 2 (c)))))
  (testing "Invalidation during load test"
    (let [a (atom 0)
          c (m/memo (fn [] (Thread/sleep 300) (swap! a inc)) inf)]
      (future (Thread/sleep 10)
              (m/memo-clear! c))
      (is (= 2 (c))))))

(deftest ret-ex-fn-test
  (testing "returns transformed-exception"
    (let [e (RuntimeException.)
          c (m/memo (fn [] (Thread/sleep 100)
                      (throw (IOException.)))
                    (assoc inf mc/ret-ex-fn (fn [_ ee] (when (instance? IOException ee) e))))
          f1 (future (try (c) (catch Exception e e)))
          f2 (future (try (c) (catch Exception e e)))]
      (is (= e @f1))
      (is (= e @f2)))))

(deftest variable-expiry-test
  (testing "Variable expiry"
    (let [c (m/memo
              identity
              (assoc inf mcc/expiry
                         (reify Expiry
                           (ttl [this _ k v] v)
                           (fade [this _ k v]))))]
      (c 1)
      (c 2)
      (c 3)
      (Thread/sleep 1100)
      (is (= {'(2) 2 '(3) 3} (m/as-map c)))))
  (testing "Variable expiry fade"
    (let [c (m/memo
              identity
              (assoc inf mcc/expiry
                         (reify Expiry
                           (ttl [this _ k v] )
                           (fade [this _ k v] v))))]
      (c 1)
      (c 2)
      (c 3)
      (Thread/sleep 1100)
      (is (= {'(2) 2 '(3) 3} (m/as-map c)))))
  (testing "variable expiry via meta"
    (let [c (m/memo
              #(with-meta {} {mc/ttl (long (+ 1 %))})
              (assoc inf mcc/expiry mcc/meta-expiry))]
      (c 1)
      (c 2)
      (c 3)
      (Thread/sleep 1100)
      (is (= {'(1) {} '(2) {} '(3) {}} (m/as-map c)))
      (Thread/sleep 1000)
      (is (= {'(2) {} '(3) {}} (m/as-map c))))))