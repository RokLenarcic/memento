(ns memento.invalidation-timeline-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (java.util HashSet List Set)
           (memento.base InvalidationTimeline)))

(defn- java-set [& values]
  (HashSet. values))

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
