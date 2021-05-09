# Changing the key for cached entry

Add `:memento.core/key-fn` to cache or mount config (or use `mc/key-fn` value) to specify a function with which to manipulate
the key cache will use for the entry.

Example building on previous suggested `cache/inf` cache configuration:

```clojure
(defn get-person-by-id [db-conn account-id person-id] {})

; when creating the cache key, remove db connection
(m/memo #'get-person-by-id (assoc cache/inf-cache mc/key-fn #(remove db-conn? %)))
; or more explicit
(m/memo #'get-person-by-id {mc/type mc/guava mc/key-fn #(remove db-conn? %)})
```

When creating the cache key, remove db connection, so the cache uses `[account-id person-id]` as key.
Thus calling the function with different db connection but same ids returns the cached value.

Another example:
```clojure
(defn return-my-user-info-json [http-request]
  (load-user (-> http-request :session :user-id)))

;; clearly the cache hit is based on a deeply nested property out of a huge request map
;; so we want to use that as basis for caching
(m/memo #'return-my-user-info-json (assoc cache/inf-cache mc/key-fn #(-> % first :session :user-id)))
```

**This is both a mount conf setting, and a cache setting.** The obvious difference is that specifying
`key-fn` for the Cache will affect all functions using that cache and in mount conf, only that one function will
be affected. If using 2-arg `memo`, then this setting is applied to mount conf.
