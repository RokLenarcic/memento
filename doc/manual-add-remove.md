# Manually evict entries

You can manually evict entries:

```clojure
; invalidate everything, also works on MountPoint instances
(m/memo-clear! memoized-function)
; invaliate an arg-list, also works on MountPoint instances
(m/memo-clear! memoized-function arg1 arg2 ...)
```

You can manually evict all entries in a Cache instance:

```clojure
(m/memo-clear-cache! cache-instance)
```

# Manually add entries

You can add entries to a function's cache at any time:

```clojure
; also works on MountPoint instances
(m/memo-add! memoized-function {[arg1 arg2] result})
```
