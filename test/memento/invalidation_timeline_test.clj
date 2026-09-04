(ns memento.invalidation-timeline-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (java.util HashSet List Set)
           (java.util.concurrent TimeUnit)
           (memento.base InvalidationTimeline)))

(defn- java-set [& values]
  (if (seq values) (HashSet. values) (HashSet.)))

(deftest invalidation-timeline-api-test
  (testing "detects invalidation active when an operation starts"
    (let [timeline (InvalidationTimeline.)
          invalidation (.startInvalidation timeline (List/of :a))
          operation (.startOperation timeline)]
      (is (.hasActiveInvalidation timeline (java-set :a)))
      (.endInvalidation timeline invalidation)
      (is (.invalidated timeline operation (java-set :a)))
      (is (not (.invalidated timeline operation (java-set :b))))))
  (testing "detects invalidation fully contained within an operation"
    (let [timeline (InvalidationTimeline.)
          operation (.startOperation timeline)
          invalidation (.startInvalidation timeline (List/of :a))]
       (.endInvalidation timeline invalidation)
       (is (.invalidated timeline operation (java-set :a)))
       (is (not (.hasActiveInvalidation timeline (java-set :a))))))
  (testing "tracks overlapping invalidations with counts"
    (let [timeline (InvalidationTimeline.)
          first-invalidation (.startInvalidation timeline (List/of :a))
          second-invalidation (.startInvalidation timeline (List/of :a))]
      (.endInvalidation timeline first-invalidation)
      (is (.hasActiveInvalidation timeline (java-set :a)))
      (.endInvalidation timeline second-invalidation)
      (is (not (.hasActiveInvalidation timeline (java-set :a))))))
  (testing "ignores empty invalidations"
       (let [timeline (InvalidationTimeline.)
           operation (.startOperation timeline)
           invalidation (.startInvalidation timeline (List/of))]
       (.endInvalidation timeline invalidation)
       (is (not (.invalidated timeline operation (java-set :a))))))
  (testing "rejects handles from another timeline"
    (let [first-timeline (InvalidationTimeline.)
          second-timeline (InvalidationTimeline.)
          operation (.startOperation first-timeline)
          invalidation (.startInvalidation first-timeline (List/of :a))]
      (is (thrown? IllegalArgumentException
                   (.invalidated second-timeline operation (java-set :a))))
      (is (thrown? IllegalArgumentException
                   (.endInvalidation second-timeline invalidation)))
      (.endInvalidation first-timeline invalidation))))

(deftest await-quiescent-test
  (testing "returns immediately when nothing is locked out"
    (let [timeline (InvalidationTimeline.)]
      (.awaitQuiescent timeline (java-set :a))
      (.awaitQuiescent timeline nil)
      (.awaitQuiescent timeline (java-set))))
  (testing "returns immediately for unrelated ids"
    (let [timeline (InvalidationTimeline.)
          invalidation (.startInvalidation timeline (List/of :a))]
      (.awaitQuiescent timeline (java-set :b))
      (.endInvalidation timeline invalidation)))
  (testing "blocks until the lockout ends"
    (let [timeline (InvalidationTimeline.)
          invalidation (.startInvalidation timeline (List/of :a))
          waiter (future (.awaitQuiescent timeline (java-set :a)) :released)]
      (is (= :timeout (deref waiter 200 :timeout)))
      (.endInvalidation timeline invalidation)
      (is (= :released (deref waiter 5000 :timeout)))))
  (testing "blocks until the last of several overlapping lockouts ends"
    (let [timeline (InvalidationTimeline.)
          older (.startInvalidation timeline (List/of :a))
          newer (.startInvalidation timeline (List/of :a))
          waiter (future (.awaitQuiescent timeline (java-set :a)) :released)]
      (.endInvalidation timeline newer)
      (is (= :timeout (deref waiter 200 :timeout)))
      (.endInvalidation timeline older)
      (is (= :released (deref waiter 5000 :timeout)))))
  (testing "a waiter can be interrupted"
    (let [timeline (InvalidationTimeline.)
          invalidation (.startInvalidation timeline (List/of :a))
          outcome (promise)
          waiter (Thread. ^Runnable
                          (fn []
                            (try
                              (.awaitQuiescent timeline (java-set :a))
                              (deliver outcome :released)
                              (catch InterruptedException _
                                (deliver outcome :interrupted)))))]
      (.start waiter)
      (Thread/sleep 100)
      (.interrupt waiter)
      (is (= :interrupted (deref outcome 5000 :timeout)))
      (.endInvalidation timeline invalidation)))
  (testing "times out with a diagnostic when a lockout remains open"
    (let [timeline (InvalidationTimeline. 20 TimeUnit/MILLISECONDS)
          invalidation (.startInvalidation timeline (List/of :a))]
      (is (thrown-with-msg? IllegalStateException
                            #"did not complete within 20 milliseconds.*leaked or waiting on its own lockout.*:a"
                            (.awaitQuiescent timeline (java-set :a))))
      (.endInvalidation timeline invalidation))))
