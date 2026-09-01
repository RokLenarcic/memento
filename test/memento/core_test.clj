(ns memento.core-test
  (:require [clojure.test :refer :all]
            [memento.base :as b]
            [memento.core :as m :refer :all]
            [memento.config :as mc]
            [memento.caffeine.config :as mcc])
  (:import (java.io IOException)
           (memento.base EntryMeta ICache)
           (memento.caffeine Expiry SecondaryIndex)
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

(deftest stale-secondary-index-does-not-remove-replaced-entry
  (let [f (memo (fn [x] (with-tag-id x :tag :old)) :tag inf)]
    (is (= 1 (f 1)))
    (is (= f (memo-add! f {[1] (with-tag-id 10 :tag :new)})))
    (is (= 10 (f 1)))
    (is (= {[1] 10} (do (memo-clear-tag! :tag :old) (as-map f))))
    (is (= {} (do (memo-clear-tag! :tag :new) (as-map f))))))

(deftest batch-write-epochs-distinguish-collapsed-keys
  (let [f (memo identity (assoc inf mc/key-fn (constantly [])))]
    (memo-add! f (array-map [1] (with-tag-id :old :batch :old)
                              [2] (with-tag-id :new :batch :new)))
    (is (= :new (f 1)))
    (memo-clear-tag! :batch :old)
    (is (= :new (f 1)))
    (memo-clear-tag! :batch :new)
    (is (empty? (as-map f)))))

(deftest overlapping-tag-invalidations-test
  (let [tag-invalidation SecondaryIndex/INSTANCE
        tag-idents #{[:tag 1]}
        older (.startInvalidation tag-invalidation tag-idents)
        newer (.startInvalidation tag-invalidation tag-idents)]
    (is (.hasActiveInvalidation tag-invalidation tag-idents))
    (.endInvalidation tag-invalidation older)
    (is (.hasActiveInvalidation tag-invalidation tag-idents))
    (.endInvalidation tag-invalidation newer)
    (is (not (.hasActiveInvalidation tag-invalidation tag-idents)))
    (is (not (.hasActiveInvalidation tag-invalidation nil)))
    (is (not (.hasActiveInvalidation tag-invalidation #{}))))
  (testing "older invalidation remains visible when a newer one finishes first"
    (let [tag-invalidation SecondaryIndex/INSTANCE
          tag-idents #{[:tag 1]}
          older (.startInvalidation tag-invalidation tag-idents)
          newer (.startInvalidation tag-invalidation tag-idents)]
      (.endInvalidation tag-invalidation newer)
      (is (.hasActiveInvalidation tag-invalidation tag-idents))
      (.endInvalidation tag-invalidation older)
      (is (not (.hasActiveInvalidation tag-invalidation tag-idents))))))

(deftest concurrent-tag-invalidation-timeline-test
  (let [secondary-index SecondaryIndex/INSTANCE
        ids (mapv #(vector :concurrent-timeline %) (range 100))
        invalidations (doall (map deref
                                  (map #(future [% (.startInvalidation secondary-index [%])]) ids)))
        ended? (atom false)]
    (try
      (is (every? #(.hasActiveInvalidation secondary-index #{%}) ids))
      (dorun (map deref
                  (map (fn [[id invalidation]]
                         (future (.endInvalidation secondary-index invalidation)))
                       invalidations)))
      (reset! ended? true)
      (is (not-any? #(.hasActiveInvalidation secondary-index #{%}) ids))
      (finally
        (when-not @ended?
          (run! (fn [[_ invalidation]]
                  (.endInvalidation secondary-index invalidation))
                invalidations))))))

(deftest tagged-invalidation-across-caches-test
  (testing "tag invalidation clears matching entries across cache instances"
    (let [calls-a (atom 0)
          calls-b (atom 0)
          a (m/memo (fn [] (m/with-tag-id (swap! calls-a inc) :shared 1))
                    inf)
          b (m/memo (fn [] (m/with-tag-id (swap! calls-b inc) :shared 1))
                    inf)]
      (is (= [1 1] [(a) (b)]))
      (is (= {nil 1} (as-map a)))
      (is (= {nil 1} (as-map b)))
      (memo-clear-tag! :shared 1)
      (is (= {} (as-map a)))
      (is (= {} (as-map b)))
      (is (= [2 2] [(a) (b)]))
      (is (= {nil 2} (as-map a)))
      (is (= {nil 2} (as-map b))))))

(deftest tagged-invalidation-across-scoped-caches-test
  (let [calls (atom 0)
        root-cache (create inf)
        scoped-a (create inf)
        scoped-b (create inf)
        f (m/bind (fn [] (m/with-tag-id (swap! calls inc) :user-id 1))
                  {mc/tags :scoped}
                  root-cache)
        ready-a (promise)
        ready-b (promise)
        release (promise)
        run-in-scope (fn [cache ready]
                       (future
                         (with-caches :scoped (constantly cache)
                           (f)
                           (deliver ready true)
                           @release
                           (f))))]
    (is (= 1 (f)))
    (let [result-a (run-in-scope scoped-a ready-a)
          result-b (run-in-scope scoped-b ready-b)]
      @ready-a
      @ready-b
      (is (= 3 @calls))
      (memo-clear-tag! :user-id 1)
      (is (empty? (b/as-map root-cache)))
      (is (empty? (b/as-map scoped-a)))
      (is (empty? (b/as-map scoped-b)))
      (deliver release true)
      (is (number? @result-a))
      (is (number? @result-b))
      (is (= 5 @calls))
      (is (number? (f)))
      (is (= 6 @calls)))))

(deftest secondary-index-does-not-require-mount-tag-test
  (let [tag :unmounted-secondary-key
        cache (create inf)
        seed (m/bind (fn [] (m/with-tag-id :stale tag 1)) {} cache)
        tag-id [tag 1]]
    (seed)
    (is (= 1 (count (b/as-map cache))))
    (memo-clear-tags! tag-id)
    (is (empty? (b/as-map cache)))))

(deftest public-invalidation-lifecycle-test
  (let [cache (create inf)
        f (m/bind (fn [value] (m/with-tag-id value :lifecycle value)) {} cache)]
    (f 1)
    (let [finish! (m/start-invalidation! [[:lifecycle 1]])]
      (is (= 1 (count (b/as-map cache))))
      (finish! true)
      (is (empty? (b/as-map cache)))
      (is (thrown-with-msg? IllegalStateException #"already completed"
                            (finish! true))))))

(deftest with-invalidation-test
  (let [cache (create inf)
        f (m/bind (fn [value] (m/with-tag-id value :with-invalidation value)) {} cache)]
    (f 1)
    (m/with-invalidation [[:with-invalidation 1]]
      :updated)
    (is (empty? (b/as-map cache)))
    (f 1)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"write failed"
                          (m/with-invalidation [[:with-invalidation 1]]
                            (throw (ex-info "write failed" {})))))
    (is (= 1 (count (b/as-map cache))))))

(deftest secondary-invalidation-dispatch-test
  (let [events (atom [])
        ids [[:dispatch 1]]
        first-type ::first-invalidator
        second-type ::second-invalidator]
    (try
      (.addMethod ^clojure.lang.MultiFn b/start-secondary-invalidation! first-type
                  (fn [_ actual-ids]
                    (swap! events conj [:start first-type actual-ids])
                    :first-started))
      (.addMethod ^clojure.lang.MultiFn b/start-secondary-invalidation! second-type
                  (fn [_ actual-ids]
                    (swap! events conj [:start second-type actual-ids])
                    :second-started))
      (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! first-type
                  (fn [_ actual-ids state]
                    (swap! events conj [:invalidate first-type actual-ids state])
                    (throw (ex-info "first backend failed" {}))))
      (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! second-type
                  (fn [_ actual-ids state]
                    (swap! events conj [:invalidate second-type actual-ids state])
                    :second-invalidated))
      (.addMethod ^clojure.lang.MultiFn b/end-secondary-invalidation! first-type
                  (fn [_ actual-ids state]
                    (swap! events conj [:end first-type actual-ids state])))
      (.addMethod ^clojure.lang.MultiFn b/end-secondary-invalidation! second-type
                  (fn [_ actual-ids state]
                    (swap! events conj [:end second-type actual-ids state])))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"first backend failed"
                            (apply memo-clear-tags! ids)))
      (is (= [:start :start :invalidate :invalidate :end :end]
             (mapv first @events)))
      (is (every? #(= ids (nth % 2)) @events))
      (is (= #{[first-type :first-started]
               [second-type :second-started]}
             (set (map (juxt second #(nth % 3))
                       (filter #(= :invalidate (first %)) @events)))))
      (is (= #{[first-type :first-started]
               [second-type :second-invalidated]}
             (set (map (juxt second #(nth % 3))
                       (filter #(= :end (first %)) @events)))))
      (finally
        (remove-method b/start-secondary-invalidation! first-type)
        (remove-method b/start-secondary-invalidation! second-type)
        (remove-method b/invalidate-secondary! first-type)
        (remove-method b/invalidate-secondary! second-type)
        (remove-method b/end-secondary-invalidation! first-type)
        (remove-method b/end-secondary-invalidation! second-type)))))

(deftest caffeine-lockout-starts-before-slow-backend-invalidation-test
  (let [backend-type ::slow-backend
        started (promise)
        release (promise)
        load-started (promise)
        load-release (promise)
        calls (atom 0)
        f (memo (fn []
                  (deliver load-started true)
                  @load-release
                  (with-tag-id (swap! calls inc) :phase 1))
                inf)]
    (.addMethod ^clojure.lang.MultiFn b/start-secondary-invalidation! backend-type
                (fn [_ _] nil))
    (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! backend-type
                (fn [_ _ state]
                  (deliver started true)
                  @release
                  state))
    (try
      (let [invalidation (future (memo-clear-tag! :phase 1))]
        @started
        (let [load (future (f))]
          @load-started
          (deliver release true)
          @invalidation
          (deliver load-release true)
          (is (= 2 @load))
          (is (= 2 @calls))))
      (finally
        (remove-method b/start-secondary-invalidation! backend-type)
        (remove-method b/invalidate-secondary! backend-type)))))

(deftest caffeine-load-finishing-during-active-invalidation-retries-test
  (let [backend-type ::slow-end
        invalidated (promise)
        release-invalidation (promise)
        load-started (promise)
        release-load (promise)
        retry-started (promise)
        release-retry (promise)
        calls (atom 0)
        f (memo (fn []
                  (let [n (swap! calls inc)]
                    (case n
                      1 (do (deliver load-started true) @release-load)
                      2 (do (deliver retry-started true) @release-retry)
                      nil)
                    (with-tag-id n :active-finish 1)))
                inf)]
    (try
      (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! backend-type
                  (fn [_ _ state]
                    (deliver invalidated true)
                    @release-invalidation
                    state))
      (let [invalidation (future (memo-clear-tag! :active-finish 1))]
        @invalidated
        (let [load (future (f))]
          @load-started
          (deliver release-load true)
          @retry-started
          (deliver release-invalidation true)
          @invalidation
          (deliver release-retry true)
          (is (= 3 @load))
          (is (= 3 @calls))))
      (finally
        (remove-method b/invalidate-secondary! backend-type)))))

(deftest secondary-invalidation-start-failure-skips-invalidators-test
  (let [events (atom [])
        good ::good-start
        bad ::bad-start]
    (try
      (.addMethod ^clojure.lang.MultiFn b/start-secondary-invalidation! good
                  (fn [_ _] (swap! events conj :good-start) :state))
      (.addMethod ^clojure.lang.MultiFn b/end-secondary-invalidation! good
                  (fn [_ _ state] (swap! events conj [:good-end state])))
      (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! good
                  (fn [_ _ state] (swap! events conj :good-invalidate) state))
      (.addMethod ^clojure.lang.MultiFn b/start-secondary-invalidation! bad
                  (fn [_ _] (swap! events conj :bad-start) (throw (ex-info "start failed" {}))))
      (.addMethod ^clojure.lang.MultiFn b/invalidate-secondary! bad
                  (fn [_ _ state] (swap! events conj :bad-invalidate) state))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"start failed"
                            (memo-clear-tag! :start-failure 1)))
      (is (not-any? #{:good-invalidate :bad-invalidate} @events))
      (is (some #{[:good-end :state]} @events))
      (finally
        (run! (fn [multifn]
                (run! #(remove-method multifn %) [good bad]))
              [b/start-secondary-invalidation!
               b/invalidate-secondary!
               b/end-secondary-invalidation!])))))

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
          c (m/memo (fn [] (Thread/sleep 300)
                      (m/with-tag-id (swap! a inc) :xx 1))
                    (assoc inf mc/tags :xx))]
      (future (Thread/sleep 15)
              (m/memo-clear-tag! :xx 1))
      (is (= 2 (c)))))
  (testing "tag invalidation during load does not store stale result"
    (let [started (promise)
          release? (atom false)
          a (atom 0)
          c (m/memo (fn []
                      (deliver started true)
                      (while (not @release?)
                        (Thread/onSpinWait))
                      (m/with-tag-id (swap! a inc) :yy 1))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear-tag! :yy 1)
      (reset! release? true)
      (is (= 2 @load))
      (is (= {nil 2} (as-map c)))))
  (testing "tag invalidation fully contained within a load retries"
    (let [started (promise)
          release (promise)
          calls (atom 0)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (when (= n 1)
                          (deliver started true)
                          @release)
                        (m/with-tag-id n :contained 1)))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear-tag! :contained 1)
      (deliver release true)
      (is (= 2 @load))
      (is (= 2 @calls))
      (is (= {nil 2} (as-map c)))))
  (testing "tag invalidation during load coordinates across caches"
    (let [started (promise)
          release? (atom false)
          a-calls (atom 0)
          b-calls (atom 0)
          a (m/memo (fn []
                      (deliver started true)
                      (while (not @release?)
                        (Thread/onSpinWait))
                      (m/with-tag-id (swap! a-calls inc) :zz 1))
                    inf)
          b (m/memo (fn [] (m/with-tag-id (swap! b-calls inc) :zz 1))
                    inf)
          load (future (a))]
      (is (= 1 (b)))
      @started
      (m/memo-clear-tag! :zz 1)
      (reset! release? true)
      (is (= 2 @load))
      (is (= {nil 2} (as-map a)))
      (is (= {} (as-map b)))
      (is (= 2 (b)))
      (is (= {nil 2} (as-map b)))))
  (testing "segment invalidation during load does not store stale result"
    (let [started (promise)
          release? (atom false)
          a (atom 0)
          c (m/memo (fn []
                      (deliver started true)
                      (while (not @release?)
                        (Thread/onSpinWait))
                      (swap! a inc))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear! c)
      (reset! release? true)
      (is (= 2 @load))
      (is (= {nil 2} (as-map c)))))
  (testing "direct invalidation releases the rejected load's timeline"
    (let [started (promise)
          release? (atom false)
          calls (atom 0)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (when (= n 1)
                          (deliver started true)
                          (while (not @release?)
                            (Thread/onSpinWait)))
                        n))
                    inf)
          load (future (c))]
      @started
      (let [promise (first (vals (.asMap (:caffeine-cache (m/active-cache c)))))]
        (is (instance? memento.caffeine.SpecialPromise promise))
        (is (.hasTimeline ^memento.caffeine.SpecialPromise promise))
        (m/memo-clear! c)
        (is (not (.hasTimeline ^memento.caffeine.SpecialPromise promise))))
      (reset! release? true)
      (is (= 2 @load))
      (is (= 2 @calls))))
  (testing "secondary invalidation releases the rejected load's timeline"
    (let [started (promise)
          release? (atom false)
          calls (atom 0)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (when (= n 2)
                          (deliver started true)
                          (while (not @release?)
                            (Thread/onSpinWait)))
                        (m/with-tag-id n :timeline-release 1)))
                    inf)]
      (is (= 1 (c)))
      ;; Leave the old secondary-index pointer behind, then let it encounter the new promise.
      (m/memo-clear! c)
      (let [load (future (c))]
        @started
        (let [promise (first (vals (.asMap (:caffeine-cache (m/active-cache c)))))]
          (is (.hasTimeline ^memento.caffeine.SpecialPromise promise))
          (m/memo-clear-tag! :timeline-release 1)
          (is (not (.hasTimeline ^memento.caffeine.SpecialPromise promise))))
        (reset! release? true)
        (is (= 3 @load))
        (is (= 3 @calls)))))
  (testing "cache invalidation during load does not store stale result"
    (let [started (promise)
          release? (atom false)
          a (atom 0)
          c (m/memo (fn []
                      (deliver started true)
                      (while (not @release?)
                        (Thread/onSpinWait))
                      (swap! a inc))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear-cache! (m/active-cache c))
      (reset! release? true)
      (is (= 2 @load))
      (is (= {nil 2} (as-map c)))))
  (testing "direct invalidation clears its interrupt before retry"
    (let [started (promise)
          release? (atom false)
          retry-interrupted? (atom nil)
          calls (atom 0)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (if (= n 1)
                          (do
                            (deliver started true)
                            (while (not @release?)
                              (Thread/onSpinWait)))
                          (reset! retry-interrupted? (.isInterrupted (Thread/currentThread))))
                        n))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear! c)
      (reset! release? true)
      (is (= 2 @load))
      (is (false? @retry-interrupted?))))
  (testing "timeline-only retry preserves an unrelated interrupt"
    (let [started (promise)
          release? (atom false)
          loader-thread (promise)
          retry-interrupted? (atom nil)
          calls (atom 0)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (if (= n 1)
                          (do
                            (deliver loader-thread (Thread/currentThread))
                            (deliver started true)
                            (while (not @release?)
                              (Thread/onSpinWait)))
                          (do
                            (reset! retry-interrupted? (.isInterrupted (Thread/currentThread)))
                            (Thread/interrupted)))
                        (m/with-tag-id n :unrelated-interrupt 1)))
                    inf)
          load (future (c))]
      @started
      (m/memo-clear-tag! :unrelated-interrupt 1)
      (.interrupt ^Thread @loader-thread)
      (reset! release? true)
      (is (= 2 @load))
      (is (true? @retry-interrupted?))))
  (testing "load started after invalidation can publish result"
    (let [a (atom 0)
          c (m/memo (fn [] (swap! a inc)) inf)]
      (m/memo-clear! c)
      (is (= 1 (c)))
      (is (= {nil 1} (as-map c)))))
  (testing "Invalidation during load test"
    (let [a (atom 0)
          after (atom 0)
          c (m/memo (fn [] (let [r (swap! a inc)]
                             (Thread/sleep 300)
                             [r (swap! after inc)])) inf)]
      (future (Thread/sleep 10)
              (m/memo-clear! c))
      (is (= [2 1] (c))))))

(deftest joiner-sees-retried-value-after-invalidate-during-load-test
  (testing "joiner blocked on a load that gets invalidated mid-compute re-loops and observes the retried load's value, not the stale first value"
    (let [calls (atom 0)
          started (promise)
          release? (atom false)
          c (m/memo (fn []
                      (let [n (swap! calls inc)]
                        (when (= n 1)
                          (deliver started true)
                          (while (not @release?)
                            (Thread/onSpinWait)))
                        n))
                    inf)
          loader (future (c))
          ;; wait for the loader to enter its compute
          _ @started
          joiner (future (c))]
      ;; let the joiner block on the SpecialPromise
      (Thread/sleep 50)
      ;; invalidate while the loader is still computing; the loader's deliver
      ;; must observe result==absent and refuse to install the entry, forcing
      ;; a retry. The joiner must not return the discarded first value.
      (m/memo-clear! c)
      (reset! release? true)
      (is (= 2 @loader))
      (is (= 2 @joiner))
      (is (= {nil 2} (as-map c)))
      ;; loader's first compute (1) was discarded; retried compute produced 2
      (is (= 2 @calls)))))

(deftest no-cache-concurrent-load-test
  (let [calls (atom 0)
        c (m/memo (fn []
                    (Thread/sleep 100)
                    (-> (swap! calls inc) do-not-cache))
                  inf)
        loads (doall (repeatedly 5 #(future (c))))]
    (is (= [1 1 1 1 1] (mapv deref loads)))
    (is (= 1 @calls))
    (is (= {} (as-map c)))
    (is (= 2 (c)))
    (is (= 2 @calls))
    (is (= {} (as-map c)))))

(deftest if-cached-failed-load-is-absent-test
  (let [started (promise)
        release (promise)
        c (m/memo (fn []
                    (deliver started true)
                    @release
                    (throw (IOException.)))
                  inf)
        loader (future (try (c) (catch IOException e e)))]
    @started
    (is (identical? EntryMeta/absent (b/if-cached (m/active-cache c) (.segment c) nil)))
    (deliver release true)
    (is (instance? IOException @loader))
    (is (identical? EntryMeta/absent (b/if-cached (m/active-cache c) (.segment c) nil)))))

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
