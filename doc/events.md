# Events
You can fire an event at a memoized function. The target can be a particular function (or MountPoint), or
you can specify a tag (and all tagged functions get the event). Each function can configure its own handler for
events. Event can be any object, I suggest you use a structure that will enable event handlers to distinguish
events.

Event handler is a function of two arguments, the MountPoint it's been triggered (most core functions work on those)
and the event.

Main use case is to enable adding entries to different functions from same data. Example:

```clojure
(defn get-project-name
  "Returns project name"
  [project-id])

(m/memo #'get-project-name inf)

(defn get-project-owner
  "Returns project's owner user ID"
  [project-id])

(m/memo #'get-project-owner inf)

(defn get-user-projects
  "Returns a big expensive list"
  [user-id]
  (let [project-list '...]
    project-list))
```

In that example, when `get-user-projects` is called, we might load over a 100 projects, and we'd hate to waste that
and not inform `get-project-name` and `get-project-owner` about the facts we've established here, especially since we
might be calling these smaller functions in a loop right after fetching the big list.

Here's a way to make sure data is reused by manually pushing entries into the caches as supported by most caching libs:

```clojure
(defn get-user-projects
  "Returns a big expensive list"
  [user-id]
  (let [project-list '...]
    ;; preload entries for seen projects into caches
    (m/memo-add! get-project-name
                 (zipmap (map (comp list :id) project-list)
                         (map :name project-list)))
    (m/memo-add! get-project-owner
                 (zipmap (map (comp list :id) project-list)
                         (repeat user-id)))
    project-list))
```

The problem with this solution is that it is an absolute nightmare to maintain:
- adding/removing data consuming functions like `get-project-name` means that I have to also fix producing
  functions like `get-user-projects`
- worse yet, the producer function has to be aware of what the argument list of consuming function looks like
  and how the output of that function is related to that. For instance if I change arg list for `get-project-owner`
  I must fix the `get-user-projects` code that pushes cache entries
- if I want additional producers like `get-user-projects` then each such producer must implement all these changes
  and each has a massive block to feed all the consumers


I can use events instead and co-locate the code that feeds the cache with the function:

```clojure
(defn get-project-name
  "Returns project name"
  [project-id])

(m/memo #'get-project-name
        (assoc inf
          mc/evt-fn (m/evt-cache-add
                      :project-seen
                      (fn [{:keys [name id]}] {[id] name}))
          mc/tags [:project]))

(defn get-project-owner
  "Returns project's owner user ID"
  [project-id])

(m/memo #'get-project-owner
        (assoc inf
          mc/evt-fn (m/evt-cache-add
                      :project-seen
                      (fn [{:keys [id user-id]}] {[id] user-id}))
          mc/tags [:project]))

(defn get-user-projects
  "Returns a big expensive list"
  [user-id]
  (let [project-list '...]
    (doseq [p project-list]
      (m/fire-event! :project [:project-seen (assoc p :user-id user-id)]))
    project-list))
```

We're using the `evt-cache-add` convenience function that assumes event shape is a
vector of type + payload and that the intent is to add entries to the cache.

In this case the producer function is only concerned with firing events at tagged caches.
It doesn't need to consider the number of shape of consumers.

The caching declaration of consumer functions is where there the cache feeding logic is located,
which makes things manageable.
