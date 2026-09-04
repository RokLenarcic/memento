# Migration Guide

## Migrating to Version 3.0

Version 3.0 removes the deprecated tag-pair invalidation API. Use secondary IDs directly; a composite value such as `[tag id]` preserves the former pairing convention.

### Breaking Changes

1. **Tag-pair APIs removed**:
   ```clojure
   ;; Old
   (m/with-tag-id value :user user-id)
   (m/memo-clear-tag! :user user-id)
   (m/memo-clear-tags! [:user user-id] [:order order-id])

   ;; New
   (m/with-sec-id value [:user user-id])
   (m/memo-clear-sec-id! [:user user-id])
   (m/start-invalidation! [:user user-id] [:order order-id])
   ```

2. **Previously deprecated Guava aliases removed**:
   - Replace `memento.guava` with `memento.caffeine`.
   - Replace `memento.guava.config` with `memento.caffeine.config`.
   - Replace `mc/guava` or `:memento.core/guava` with `mc/caffeine`.

3. **No-op `mc/concurrency` setting removed**: delete it from cache configuration maps.

Prefer `with-invalidation` when the invalidation surrounds a write:

```clojure
(m/with-invalidation [[:user user-id]]
  (db/update-user! user-id changes))
```

Concurrent calls whose results carry a locked-out secondary ID block until the write finishes,
then load fresh data. Do not call such a memoized function from inside its own
`with-invalidation` body; after one minute Memento throws `IllegalStateException` describing the
likely self-lockout.
If you use `start-invalidation!` directly, always call the returned completion function, including
on failure. Otherwise affected callers time out after one minute with an explanatory exception.

## Migrating to Version 2.0

Version 2.0 upgrades from Caffeine 2 to Caffeine 3.

### Requirements Change

- **Minimum Java version**: Java 11 (was Java 8)

### Breaking Changes

None for user-facing API. The upgrade is seamless if you're already on Java 11+.

## Migrating to Version 1.0

Version 1.0 switched from Guava Cache to Caffeine as the underlying cache implementation.

### Why the Change?

- **Performance**: Caffeine is significantly faster than Guava Cache
- **Smaller dependency**: No longer pulls in the entire Guava library
- **Active development**: Caffeine is actively maintained with modern Java features

### Breaking Changes

1. **Cache type keyword changed**:
   ```clojure
   ;; Old (deprecated)
   {mc/type :memento.core/guava}
   
   ;; New
   {mc/type :memento.core/caffeine}
   ;; Or simply
   {mc/type mc/caffeine}
   ```

2. **Namespace changes**:
   - `memento.guava` is deprecated, use `memento.caffeine`
   - `memento.guava.config` is deprecated, use `memento.caffeine.config`

### Migration Steps

1. Update your Java version to 11+ (required for 2.0)
2. Replace `:memento.core/guava` with `:memento.core/caffeine` (or `mc/caffeine`)
3. Replace `memento.guava` requires with `memento.caffeine`
4. Replace `memento.guava.config` requires with `memento.caffeine.config`

### Backward Compatibility

The deprecated Guava namespaces and aliases remained available through version 2.1. Version 3.0
removes them; see the version 3.0 migration steps above.
