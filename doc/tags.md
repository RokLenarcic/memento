# Tags

You can add tags to the caches. You can run actions on caches with specific tags.

You can specify them via `:memento.core/tags` key (also `mc/tags` value),
or you can simply specify them instead of conf map, which creates a tagged cache
of noop type (that you can replace later).

```clojure
(m/memo {mc/tags [:request-scope :person]} #'get-person-by-id)
(m/memo [:request-scope :person] #'get-person-by-id)
(m/memo :person #'get-person-by-id)
```

#### Utility

You can fetch tags on a memoized function.

```clojure
(m/tags get-person-by-id)
=> [:person]
```

You can fetch all mount points of functions that are tagged by a specific tag:

```clojure
(m/mounts-by-tag :person)
=> #{#memento.mount.TaggedMountPoint{...}}
```

#### Change / update cache within a scope

```clojure
(m/with-caches :person (constantly (m/create cache/inf-cache))
  (get-person-by-id db-spec 1 12)
  (get-person-by-id db-spec 1 12)
  (get-person-by-id db-spec 1 12))
```

Every memoized function (mountpoint) inside the block has its cache updated to the result of the
provided function. In this example, all the `:person` tagged functions will use the same unbounded cache
within the block. This effectively stops them from using any previously cached values and any values added to
cache are dropped when block is exited.

**This is extremely useful to achieve request scoped caching.**

#### Updating / changing cache instance permanently

You can update Cache instances of all functions tagged by a specific tag. This will modify root binding
if not inside `with-caches`, otherwise it will modify the binding.

```clojure
(m/update-tag-caches! :person (constantly (m/create cache/inf-cache)))
```

All `:person` tagged memoized functions will from this point on use a new empty unbounded cache.

#### Applying operations to tagged memoized functions

Use `mounts-by-tag` to grab mount points and then apply any of the core functions to them.

```clojure
(doseq [f (m/mounts-by-tag :person)]
  (m/memo-clear! f))
```

#### Invalidate entries by a tag + ID combo

You can add tag + ID pairs to cached values. This can be later used to invalidate these
entried based on that ID.

ID can be a number like `1` or something complex like a `[1 {:region :us}]`. You can attach multiple
IDs for same tag.

You can add the tag ID pair inside the cached function or in the ret-fn:

```clojure
(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    (-> {:status 200}
        (m/with-tag-id :person person-id)
        (m/with-tag-id :account account-id))))

(m/memo #'get-person-by-id [:person :account] cache/inf-cache)
```

Now you can invalidate all entries linked to a specified ID in any correctly tagged cache:

```clojure
(m/memo-clear-tag! :account 1)
```

This will invalidate entries with tag id `:account, 1` in all `:account` tagged functions.

As mentioned, you can move code that adds the id information to a `ret-fn`:

```clojure
; first argument is args, second is the returned value
(defn ret-fn [[_ account-id person-id :as args] resp]
  (if (<= 400 (:status resp) 599)
    (m/do-not-cache resp)
    (-> resp
        ; we can grab the data from arg list
        (m/with-tag-id :account account-id)
        (m/with-tag-id :person person-id)
        ; or we can grab it from the return value
        (m/with-tag-id :person (:id resp)))))

(defn get-person-by-id [db-conn account-id person-id]
  (if (nil? person-id)
    {:status 404}
    {:status 200 :id person-id :name ....}))

(m/memo #'get-person-by-id [:person :account] (assoc cache/inf-cache mc/ret-fn ret-fn))
```

Later you can invalidate tagged entries:

```clojure
(m/memo-clear-tag! :person 1)

;; get better atomicity with bulk operation

(m/memo-clear-tags! [:person 1] [:user 33])
```
