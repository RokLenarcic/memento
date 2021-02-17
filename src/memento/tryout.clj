(ns memento.tryout
  (:require [memento.core :as m]
    ; general cache conf keys
            [memento.config :as mc]
    ; guava specific cache conf keys
            [memento.guava.config :as mcg])
  (:import (com.google.common.cache CacheBuilder)))

(def inf-cache
  {mc/type mc/guava})

(def sized-cache
  (assoc inf-cache mc/size< 1 mcg/removal-listener (fn [f k v reason] (println f k v reason))))

(defn no-cache-error-resp [resp]
  (if (<= 400 (:status resp) 599)
    (m/do-not-cache resp)
    resp))

(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    (m/with-tag-id {:status 200} :person person-id)))

#_(m/memo #'get-person-by-id {mc/tags [:request-scope :person]})
#_(m/memo #'get-person-by-id [:request-scope :person])
(m/memo #'get-person-by-id :person sized-cache)

(let [cache (.build (CacheBuilder/newBuilder))
      f #(do (when (neg? %) (Thread/sleep 2000))
             (.invalidate cache (* -1 %))
             %)
      fneg (future (.get cache -1 #(f -1)))
      fpos (future (.get cache 1 #(f 1)))]
  @fneg
  @fpos
  (.asMap cache))

(declare b)
(defn a [x]
  (Thread/sleep 5000)
  (m/memo-clear! b 500)
  (m/with-tag-id x :person x))

(defn b [x]
  (Thread/sleep 2000)
  (m/memo-clear! a 1)
  (m/with-tag-id x :account x))

(defn c [x]
  (m/memo-clear! c 1)
  (m/with-tag-id x :status x))

(comment
  (def my-cache (m/create inf-cache))
  (m/memo #'a :person my-cache)
  (m/memo #'b :account my-cache)
  (m/memo #'c :status my-cache)
  (let [ax (future (a 1))
        bx (future (b 500))]
    (println @ax @bx)))
