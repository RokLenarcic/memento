(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'org.clojars.roklenarcic/memento)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def java-src-dir "java")

(defn add-defaults [opts]
  (assoc opts :lib lib :version version))

(defn compile-java [{:keys [basis class-dir target] :as opts}]
  (-> opts
      add-defaults
      (assoc :javac-opts ["-source" "8" "-target" "8"]
             :basis     (bb/default-basis basis)
             :src-dirs [java-src-dir]
             :class-dir (bb/default-class-dir class-dir target))
      b/javac)
  opts)

(defn test "Run the tests." [opts]
  (compile-java opts)
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      add-defaults
      (test)
      (bb/clean)
      compile-java
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      add-defaults
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      add-defaults
      (assoc :sign-releases? true)
      (bb/deploy)))
