# Memento - Caching Library Analysis

This document provides an analysis of the Memento caching library for AI assistants working on this codebase.

## Project Overview

**Memento** is a Clojure function memoization library with advanced features beyond basic caching. It provides:
- Request/scoped caching (temporarily replace caches within a scope)
- Tagged eviction via secondary index (invalidate entries by tag + ID pairs)
- Selective caching (prevent caching of specific return values)
- Tiered/multi-cache support (combine local + upstream caches)
- Shared size-based eviction across multiple functions
- Cache events system
- Variable per-entry expiry

**Current version**: 2.0.68  
**Minimum Java**: 11+  
**Primary dependency**: Caffeine 3.1.8

## Directory Structure

```
memento/
├── src/memento/           # Clojure source files
│   ├── core.clj           # Main public API (307 lines)
│   ├── base.clj           # Core abstractions and protocols (60 lines)
│   ├── config.clj         # Configuration constants (178 lines)
│   ├── mount.clj          # MountPoint implementations (151 lines)
│   ├── caffeine.clj       # Caffeine cache implementation (145 lines)
│   ├── caffeine/config.clj # Caffeine-specific config keys
│   ├── guava.clj          # DEPRECATED - redirects to caffeine
│   ├── guava/config.clj   # DEPRECATED
│   ├── multi.clj          # Tiered cache implementations (47 lines)
│   └── ns_scan.clj        # Namespace scanning utility (47 lines)
│
├── java/memento/          # Java source files (performance-critical code)
│   ├── base/              # Core interfaces
│   │   ├── ICache.java    # Main cache interface
│   │   ├── Segment.java   # Function binding info
│   │   ├── CacheKey.java  # Composite key (id + args)
│   │   ├── EntryMeta.java # Cached value wrapper with metadata
│   │   ├── LockoutMap.java # Bulk invalidation coordination
│   │   ├── LockoutTag.java
│   │   └── Durations.java
│   ├── mount/             # Mount point implementations
│   │   ├── IMountPoint.java
│   │   ├── Cached.java    # Marker interface for cached fns
│   │   ├── CachedFn.java
│   │   └── CachedMultiFn.java
│   ├── caffeine/          # Caffeine implementation
│   │   ├── CaffeineCache_.java
│   │   ├── Expiry.java
│   │   ├── SecondaryIndex.java
│   │   └── SpecialPromise.java
│   └── multi/             # Multi-cache implementations
│       ├── MultiCache.java
│       ├── TieredCache.java
│       ├── ConsultingCache.java
│       └── DaisyChainCache.java
│
├── test/memento/          # Test files
│   ├── core_test.clj      # Main API tests (429 lines)
│   ├── caffeine_test.clj  # Cache builder tests (58 lines)
│   ├── multi_test.clj     # Multi-cache tests (67 lines)
│   ├── mount_test.clj     # Mount point tests (112 lines)
│   └── ns_scan_test.clj   # Namespace scanning tests (22 lines)
│
├── doc/                   # Documentation markdown files
└── deps.edn               # Project dependencies
```

## Architecture

### Key Concepts

1. **Cache**: An instance of `ICache` that stores key-value pairs. One cache can back multiple functions.

2. **MountPoint**: Connects a function to a cache. Contains:
   - Reference to the cache (or tag for dynamic lookup)
   - Segment information (function, key-fn, id, config)
   - Event handler

3. **Segment**: Metadata about a memoized function binding:
   - `f` - The original function
   - `keyFn` - Key transformation function
   - `id` - Identifier (typically var name)
   - `conf` - Mount configuration

4. **CacheKey**: Composite key used in cache storage:
   - `id` - Segment identifier
   - `args` - Transformed function arguments

5. **Tags**: Enable scoped caching and bulk operations:
   - Functions can have multiple tags
   - `with-caches` temporarily replaces cache for a tag
   - `memo-clear-tag!` invalidates by tag + ID

### Namespace Responsibilities

| Namespace | Purpose |
|-----------|---------|
| `memento.core` | Public API - all user-facing functions |
| `memento.base` | Core abstractions, `ICache` wrapper fns, `no-cache`, `new-cache` multimethod |
| `memento.config` | Configuration key constants and time units |
| `memento.mount` | `TaggedMountPoint` and `UntaggedMountPoint` records, tag management |
| `memento.caffeine` | `CaffeineCache` record implementing `ICache` |
| `memento.multi` | Registers tiered/consulting/daisy cache types |
| `memento.ns-scan` | Auto-attach caches to annotated vars |

### Java vs Clojure Split

Java is used for performance-critical paths:
- Reduces stack depth for cached calls
- Implements `ICache` and `IMountPoint` interfaces
- Handles concurrent load coordination (`LockoutMap`)
- Secondary index for tag-based eviction

Clojure is used for:
- Public API (`memento.core`)
- Configuration and records
- Multimethod dispatch for cache types

## Cache Types

### Primary: Caffeine (`:memento.core/caffeine`)
- High-performance Java caching library
- Supports: size limits, TTL, fade (access-based), weak/soft references, statistics
- Default cache type

### None (`:memento.core/none`)
- No-op cache, doesn't cache anything
- Used when caching is globally disabled

### Multi-Cache Types

| Type | Behavior |
|------|----------|
| `:memento.core/tiered` | Fetches from local, delegates to upstream. After: entry in BOTH caches |
| `:memento.core/consulting` | Fetches from local, consults upstream. After: entry only in LOCAL cache |
| `:memento.core/daisy` | Returns from local IF PRESENT, else upstream. Entry NOT added to local |

## Configuration Keys

### Core (`memento.config` / `mc`)
- `mc/type` - Cache implementation type
- `mc/size<` - Max entries
- `mc/ttl` - Time-to-live
- `mc/fade` - Access-based expiration
- `mc/key-fn` / `mc/key-fn*` - Key transformation
- `mc/ret-fn` - Return value transformation
- `mc/ret-ex-fn` - Exception transformation
- `mc/seed` - Initial entries
- `mc/tags` - Mount point tags
- `mc/id` - Custom identifier
- `mc/evt-fn` - Event handler

### Caffeine-specific (`memento.caffeine.config` / `mcc`)
- `mcc/removal-listener` - Eviction callback
- `mcc/weight<` / `mcc/kv-weight` - Weight-based eviction
- `mcc/weak-values` / `mcc/soft-values` - Reference types
- `mcc/stats` - Enable statistics
- `mcc/ticker` - Custom time source
- `mcc/expiry` - Variable expiry

## Main Public API (`memento.core`)

### Memoization
```clojure
(m/memo fn-or-var)                    ; Memoize with defaults
(m/memo fn-or-var conf)               ; Memoize with config
(m/memo fn-or-var mount-conf cache)   ; Memoize with separate configs
(m/defmemo name [args] body)          ; Define memoized function
```

### Cache Management
```clojure
(m/create cache-conf)                 ; Create a cache
(m/bind fn-or-var mount-conf cache)   ; Bind cache to function
(m/as-map cached-fn)                  ; Get cache contents
(m/memoized? f)                       ; Check if memoized
(m/memo-unwrap cached-fn)             ; Get original function
(m/active-cache cached-fn)            ; Get underlying cache
```

### Invalidation
```clojure
(m/memo-clear! cached-fn)             ; Clear all entries
(m/memo-clear! cached-fn & args)      ; Clear specific entry
(m/memo-clear-cache! cache)           ; Clear entire cache
(m/memo-clear-tag! tag id)            ; Clear by secondary index
(m/memo-clear-tags! & [tag id] pairs) ; Bulk clear
```

### Return Value Control
```clojure
(m/do-not-cache value)                ; Prevent caching this value
(m/with-tag-id value tag id)          ; Tag value for secondary index
```

### Scoped Caching
```clojure
(m/with-caches tag cache-fn & body)   ; Temporary cache replacement
(m/update-tag-caches! tag cache-fn)   ; Permanent cache update
```

### Events
```clojure
(m/fire-event! f-or-tag event)        ; Fire event to populate caches
(m/evt-cache-add evt-type ->entries)  ; Create event handler
```

### Multi-Cache
```clojure
(m/tiered local upstream)             ; Both caches updated
(m/consulting local upstream)         ; Only local updated
(m/daisy local upstream)              ; No local update
```

## Important Implementation Details

### Concurrency
- Single ongoing load per key (Caffeine handles this)
- If key invalidated during load, load is repeated
- `LockoutMap` coordinates bulk invalidations
- Loading thread is interrupted on tag invalidation

### Secondary Index
- Entries can be tagged with `[tag id]` pairs via `with-tag-id`
- `SecondaryIndex` (Java) maintains tag-to-keys mappings
- Enables efficient invalidation by tag + ID

### Reload Guards
- Special objects clean up tag mappings when memoized functions are GCed
- Important for namespace reloading in development
- Can be disabled via `-Dmemento.reloadable=false`

## Testing

Run tests with:
```bash
clj -X:test
```

Test coverage includes:
- Basic memoization parity with `clojure.core/memoize`
- Exception handling and retries
- Thread safety
- Size-based eviction (LRU)
- TTL expiration
- Key/return transformation
- Tagged caching and scoped caches
- Events
- Multi-cache types
- Multimethod support

## Build

```bash
clj -T:build jar      # Build JAR
clj -T:build deploy   # Deploy to Clojars
```

## Deprecated Code

- `memento.guava` namespace - redirects to caffeine
- `memento.guava.config` namespace
- `:memento.core/guava` cache type

These exist for backward compatibility from version 0.x migration.

## Potential Simplification Areas

1. **Guava namespaces**: Can be removed entirely if backward compatibility not needed

2. **Java code**: Some simpler classes might be convertible to Clojure records/protocols if stack depth isn't critical

3. **Configuration**: Many configuration keys - could potentially reduce or group

4. **Multi-cache types**: Three similar implementations (tiered/consulting/daisy) with subtle differences

5. **Mount point types**: Tagged vs Untagged have significant overlap

6. **Test organization**: Tests are comprehensive but could be better organized by feature
