# Migration Guide

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

The deprecated Guava namespaces and type keyword still work but will be removed in a future version. They internally redirect to the Caffeine implementation.
