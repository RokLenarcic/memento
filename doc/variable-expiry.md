## Variable expiry

A configuration key `:memento.core/expiry` allows that you specify expiry on per
entry basis. The value needs to be an instance of `memento.caffeine.Expiry` interface.

If functions return nil, then the value of corresponding setting `ttl` or `fade` is used
instead.

Here's an example:
```clojure

(def ttl-for-person
  (reify Expiry
    (ttl [this _ k v]
      (if (demo-user? (:id v))
        ;; never changes, cache 10 days
        [10 :d]
        ;; else cache 60 seconds
        60))
    (fade [this _ k v])))

(m/memo #'get-user-by-id {mc/type mc/caffeine mc/expiry ttl-for-person})
```

An implementation `memento.config/meta-expiry` is provided. That reads meta of returned objects
for keys `memento.core/ttl` and `memento.core/fade`.

```clojure

(defn get-user-by-id [id]
  (let [ret ....]
    (with-meta
      ret
      {mc/ttl (if (demo-user? (:id ret))
                ;; never changes, cache 10 days
                [10 :d]
                ;; else cache 60 seconds
                60)})))

(m/memo #'get-user-by-id {mc/type mc/caffeine mc/expiry mc/meta-expiry})
```