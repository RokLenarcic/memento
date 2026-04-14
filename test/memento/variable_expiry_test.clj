(ns memento.variable-expiry-test
  "Comprehensive tests for Caffeine variable per-entry expiry.

   Variable expiry allows each cached entry to have its own TTL and/or fade
   duration, controlled by an implementation of the memento.caffeine.Expiry
   interface."
  (:require [clojure.test :refer :all]
            [memento.core :as m]
            [memento.config :as mc]
            [memento.caffeine.config :as mcc])
  (:import (memento.caffeine Expiry)))

(def inf
  "Base caffeine cache config with no size or time limits."
  {mc/type mc/caffeine})

;; ---------------------------------------------------------------------------
;; Custom Expiry: TTL per entry
;; ---------------------------------------------------------------------------

(deftest custom-expiry-ttl-test
  (testing "Each entry gets a TTL equal to its value (in seconds)"
    (let [c (m/memo identity
                    (assoc inf mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k v] v)
                             (fade [_ _ _k _v]))))]
      (c 1)
      (c 2)
      (c 3)
      ;; after 1.1s, entry with TTL=1s should be gone
      (Thread/sleep 1100)
      (is (= {'(2) 2 '(3) 3} (m/as-map c)))
      ;; after another 1s, entry with TTL=2s should be gone
      (Thread/sleep 1000)
      (is (= {'(3) 3} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Custom Expiry: fade (access-based) per entry
;; ---------------------------------------------------------------------------

(deftest custom-expiry-fade-test
  (testing "Each entry gets a fade duration equal to its value (in seconds)"
    (let [c (m/memo identity
                    (assoc inf mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k _v])
                             (fade [_ _ _k v] v))))]
      (c 1)
      (c 2)
      (c 3)
      (Thread/sleep 1100)
      ;; entry 1 (fade=1s) expired, 2 and 3 still alive
      (is (= {'(2) 2 '(3) 3} (m/as-map c)))))

  (testing "Accessing an entry resets its fade timer"
    (let [c (m/memo identity
                    (assoc inf mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k _v])
                             (fade [_ _ _k v] v))))]
      (c 1)
      (c 2)
      ;; repeatedly access entry 1 to keep it alive past its 1s fade
      (Thread/sleep 600)
      (c 1) ; access resets fade timer
      (Thread/sleep 600)
      (c 1) ; access resets fade timer again
      (Thread/sleep 600)
      ;; entry 1 was accessed recently so fade hasn't elapsed
      ;; entry 2 was never re-accessed but has a 2s fade, so 1.8s total is under 2s
      (is (= {'(1) 1 '(2) 2} (m/as-map c)))
      ;; now let both expire
      (Thread/sleep 2100)
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Duration format: vector [amount :unit] vs plain number
;; ---------------------------------------------------------------------------

(deftest duration-format-test
  (testing "Expiry can return durations in [amount :unit] vector format"
    (let [c (m/memo identity
                    (assoc inf mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k v]
                    ;; return TTL as milliseconds vector
                               [(* v 1000) :ms])
                             (fade [_ _ _k _v]))))]
      (c 1) ; TTL = [1000 :ms] = 1 second
      (c 2) ; TTL = [2000 :ms] = 2 seconds
      (Thread/sleep 1100)
      (is (= {'(2) 2} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Fallback to cache-level defaults when Expiry returns nil
;; ---------------------------------------------------------------------------

(deftest fallback-to-cache-defaults-test
  (testing "When Expiry.ttl returns nil, cache-level TTL is used"
    (let [c (m/memo identity
                    (assoc inf
                           mc/ttl 2 ; cache-level: 2 second TTL
                           mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k _v] nil) ; always return nil -> fallback
                             (fade [_ _ _k _v]))))]
      (c :a)
      (Thread/sleep 1100)
      ;; 2s TTL hasn't elapsed yet
      (is (= {'(:a) :a} (m/as-map c)))
      (Thread/sleep 1100)
      ;; ~2.2s total, 2s TTL has elapsed
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Mixed: some entries get custom TTL, others fall back to default
;; ---------------------------------------------------------------------------

(deftest mixed-custom-and-default-expiry-test
  (testing "Entries returning nil from ttl() use cache-level default"
    (let [c (m/memo identity
                    (assoc inf
                           mc/ttl 3 ; cache-level: 3 second TTL
                           mcc/expiry
                           (reify Expiry
                             (ttl [_ _ _k v]
                    ;; only give a short TTL to value 1
                               (when (= v 1) 1))
                             (fade [_ _ _k _v]))))]
      (c 1) ; custom TTL = 1s
      (c 2) ; nil -> cache default = 3s
      (c 3) ; nil -> cache default = 3s
      (Thread/sleep 1100)
      ;; entry 1 expired (1s TTL), entries 2 and 3 still have ~2s left
      (is (= {'(2) 2 '(3) 3} (m/as-map c)))
      (Thread/sleep 2100)
      ;; all expired now
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; meta-expiry: TTL from Clojure metadata on return values
;; ---------------------------------------------------------------------------

(deftest meta-expiry-ttl-test
  (testing "TTL is read from :memento.core/ttl metadata on the return value"
    (let [c (m/memo
             (fn [x] (with-meta {:val x} {mc/ttl (inc x)}))
             (assoc inf mcc/expiry mcc/meta-expiry))]
      (c 1) ; returns ^{mc/ttl 2} {:val 1}  -> 2s TTL
      (c 2) ; returns ^{mc/ttl 3} {:val 2}  -> 3s TTL
      (c 3) ; returns ^{mc/ttl 4} {:val 3}  -> 4s TTL
      (Thread/sleep 1100)
      ;; all still present (shortest is 2s)
      (is (= {'(1) {:val 1} '(2) {:val 2} '(3) {:val 3}}
             (m/as-map c)))
      (Thread/sleep 1100)
      ;; entry 1 expired (2s TTL)
      (is (= {'(2) {:val 2} '(3) {:val 3}}
             (m/as-map c)))
      (Thread/sleep 1100)
      ;; entry 2 expired (3s TTL)
      (is (= {'(3) {:val 3}}
             (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; meta-expiry: fade from Clojure metadata
;; ---------------------------------------------------------------------------

(deftest meta-expiry-fade-test
  (testing "Fade is read from :memento.core/fade metadata on the return value"
    (let [c (m/memo
             (fn [x] (with-meta {:val x} {mc/fade (inc x)}))
             (assoc inf mcc/expiry mcc/meta-expiry))]
      (c 1) ; fade = 2s
      (c 2) ; fade = 3s
      (Thread/sleep 1100)
      ;; access entry 1 to reset its fade timer
      (c 1)
      (Thread/sleep 1100)
      ;; entry 1 was accessed ~1.1s ago, fade is 2s -> still alive
      ;; entry 2: no access since creation ~2.2s ago, fade is 3s -> still alive
      (is (= {'(1) {:val 1} '(2) {:val 2}} (m/as-map c)))
      (Thread/sleep 2100)
      ;; both expired
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; meta-expiry: both TTL and fade on same entry
;; ---------------------------------------------------------------------------

(deftest meta-expiry-ttl-and-fade-test
  (testing "Entry with both TTL and fade in metadata: TTL controls write expiry, fade controls read expiry"
    (let [c (m/memo
             (fn [x] (with-meta {:val x} {mc/ttl 3 mc/fade 1}))
             (assoc inf mcc/expiry mcc/meta-expiry))]
      (c 1)
      ;; keep accessing to reset fade timer
      (Thread/sleep 600)
      (c 1)
      (Thread/sleep 600)
      ;; fade is 1s and we accessed 0.6s ago, so still alive
      (is (= {'(1) {:val 1}} (m/as-map c)))
      ;; stop accessing and let fade expire
      (Thread/sleep 1100)
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; meta-expiry: fallback for non-IObj values
;; ---------------------------------------------------------------------------

(deftest meta-expiry-non-iobj-fallback-test
  (testing "When value doesn't support metadata, meta-expiry returns nil and cache default applies"
    (let [c (m/memo
             identity ; numbers don't support metadata
             (assoc inf
                    mc/ttl 1
                    mcc/expiry mcc/meta-expiry))]
      (c 42)
      (Thread/sleep 600)
      ;; still alive (1s default TTL)
      (is (= {'(42) 42} (m/as-map c)))
      (Thread/sleep 600)
      ;; expired by default TTL
      (is (= {} (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Interaction with do-not-cache
;; ---------------------------------------------------------------------------

(deftest variable-expiry-with-do-not-cache-test
  (testing "do-not-cache prevents caching even when variable expiry is configured"
    (let [call-count (atom 0)
          c (m/memo
             (fn [x]
               (swap! call-count inc)
               (if (odd? x)
                 (m/do-not-cache x)
                 x))
             (assoc inf mcc/expiry
                    (reify Expiry
                      (ttl [_ _ _k _v] 10) ; long TTL
                      (fade [_ _ _k _v]))))]
      (c 1) ; odd -> do-not-cache
      (c 2) ; even -> cached
      (is (= {'(2) 2} (m/as-map c)))
      ;; calling (c 1) again should invoke the function again
      (reset! call-count 0)
      (c 1)
      (is (= 1 @call-count))
      ;; calling (c 2) should NOT invoke the function
      (reset! call-count 0)
      (c 2)
      (is (= 0 @call-count)))))

;; ---------------------------------------------------------------------------
;; Interaction with secondary index (with-tag-id)
;; ---------------------------------------------------------------------------

(deftest variable-expiry-with-tag-id-test
  (testing "with-tag-id works alongside variable expiry for tag-based invalidation"
    (let [c (m/memo
             (fn [x]
               (m/with-tag-id {:val x} :my-tag x))
              ;; mount tag :my-tag is needed so memo-clear-tag! can find the cache
             :my-tag
             (assoc inf mcc/expiry
                    (reify Expiry
                      (ttl [_ _ _k _v] 30) ; long TTL so entries won't expire during test
                      (fade [_ _ _k _v]))))]
      (c 1)
      (c 2)
      (c 3)
      (is (= {'(1) {:val 1} '(2) {:val 2} '(3) {:val 3}}
             (m/as-map c)))
      ;; invalidate by tag
      (m/memo-clear-tag! :my-tag 2)
      (is (= {'(1) {:val 1} '(3) {:val 3}}
             (m/as-map c))))))

;; ---------------------------------------------------------------------------
;; Expiry receives the correct key (args)
;; ---------------------------------------------------------------------------

(deftest expiry-receives-correct-args-test
  (testing "Expiry interface receives the function args as the key"
    (let [seen-keys (atom [])
          c (m/memo
             (fn [x y] (+ x y))
             (assoc inf mcc/expiry
                    (reify Expiry
                      (ttl [_ _ k _v]
                        (swap! seen-keys conj (vec k))
                        10)
                      (fade [_ _ _k _v]))))]
      (c 1 2)
      (c 3 4)
      ;; the keys seen by Expiry should be the argument lists
      (is (= [[1 2] [3 4]] @seen-keys)))))

;; ---------------------------------------------------------------------------
;; Expiry with key-fn transformation
;; ---------------------------------------------------------------------------

(deftest expiry-with-key-fn-test
  (testing "Expiry sees the transformed key when key-fn is used"
    (let [seen-keys (atom [])
          c (m/memo
             (fn [x] (* x x))
             (assoc inf
                    mc/key-fn (fn [args] [(first args)])
                    mcc/expiry
                    (reify Expiry
                      (ttl [_ _ k _v]
                        (swap! seen-keys conj (vec k))
                        10)
                      (fade [_ _ _k _v]))))]
      (c 5)
      ;; key-fn transforms args, Expiry should see the transformed key
      (is (= [[5]] @seen-keys)))))

;; ---------------------------------------------------------------------------
;; Regression: reading an entry during its per-entry TTL should not reset
;; the expiry to the cache-level default
;; ---------------------------------------------------------------------------

(deftest read-during-ttl-does-not-reset-expiry-test
  (testing "Cache read during per-entry TTL window does not extend expiry to cache-level default"
    (let [call-count (atom 0)
          c (m/memo
             (fn [x]
               (swap! call-count inc)
               x)
             (assoc inf
                    mc/ttl [1 :h]
                    mcc/expiry
                    (reify Expiry
                      (ttl [_ _ _k v] (when (= v :short) [1 :s]))
                      (fade [_ _ _k _v]))))]
      ;; :short gets 1s TTL, :long gets 1h default
      (c :short)
      (c :long)
      ;; read :short during its 1s TTL window
      (Thread/sleep 500)
      (c :short)
      ;; wait for the 1s TTL to expire
      (Thread/sleep 700)
      ;; :short should have expired despite being read during its TTL
      (is (= {'(:long) :long} (m/as-map c)))
      ;; calling :short again should re-invoke the function
      (reset! call-count 0)
      (c :short)
      (is (= 1 @call-count)))))
