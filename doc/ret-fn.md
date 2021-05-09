# Prevent caching of a specific return value

If you want to prevent caching of a specific function return, you can wrap it in special record
using `memento.core/do-not-cache` function. Example:

```clojure
(defn get-person-by-id [db-conn account-id person-id]
  (if-let [person (db-get-person db-conn account-id person-id)]
    {:status 200 :body person} 
    (m/do-not-cache {:status 404})))
```

404 responses won't get cached, and the function will be invoked every time for those ids.

# Modifying returned value

Sticking a piece of caching logic into your function logic isn't very clean. Instead, you can
add `:memento.core/ret-fn` to cache or mount conf (or use `mc/ret-fn` value) to specify a function that can modify
the return value from a cached function before it is cached. This is useful when using the `do-not-cache` function above to
do the wrapping outside the function being cached. Example:

```clojure
; first argument is args, second is the returned value
(defn no-cache-error-resp [[db-conn account-id person-id :as args] resp]
  (if (<= 400 (:status resp) 599)
    (m/do-not-cache resp)
    resp))

(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    {:status 200}))

(m/memo #'get-person-by-id (assoc cache/inf-cache mc/ret-fn no-cache-error-resp))
```

**This is both a mount conf setting, and a cache setting. This has same consequences as with key-fn setting above.**
