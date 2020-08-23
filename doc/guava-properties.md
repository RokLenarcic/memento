# Guava cache spec

Caches and regions of the `:memento.core/guava` type are backed by this implementation.
Besides the standard configuration keys, `key-fn`, `ret-fn`, `seed`, ..., this implementation allows additional configuration:

**AS MENTIONED, YOU CAN USE NON-NAMESPACED KEYS**

### Durations

Some of the properties require that you specify a duration. In these cases you can:

- specify an integer, time unit of `seconds` will be used
- specify a vector of `[int, unit-keyword]`

The units are, from nano-seconds to days: `:ns, :us, :ms, :s, :m, :h, :d`

# Cache

### 1. `:memento.core/concurrency`

Corresponds to `.concurrencyLevel` on CacheBuilder. Integer describing
how many Sections the cache has, different Sections being able to be
concurrently accessed. Larger number = less locking in concurrent access.

Default is 4.

### 2. `:memento.core/initial-capacity`

Corresponds to `.initialCapacity` on CacheBuilder. Sets the initial capacity of the new cache.

Default is 16.

### 3. `:memento.core/size<`

Corresponds to `.maxSize` on CacheBuilder. Sets the size of cache that should not
be exceeded, value an integer denoting max count of items. It cannot be used at the same time as weight based eviction.

Guava might evict before reaching the limit and the eviction order appears to be a mix of LRU and LU, 
per Guava documentation:

```text
The cache will try to evict entries that haven't been used recently or very often. 
Warning: the cache may evict entries before this limit is exceeded,
 -- typically when the cache size is approaching the limit.
```

### 4. `:memento.core/weight<` and `:memento.core/kv-weight` 

An alternative to `size<` for cache size based eviction, similar in behaviour as above, except:

The `:weight<` integer setting specifies max number of weight units the cache can take.

The `:kv-weight` should be a function of 2-arguments `transformed key, transformed value` and return an integer, the number of
weight units this entry should take. 

**Note: key and value passed to the weight function are the values after `key-fn` and `ret-fn` have been applied** 

### 5. `:memento.core/weak-values`

Corresponds to `.weakValues` on CacheBuilder.

Boolean flag, enabling storing values using weak references.

This allows entries to be garbage-collected if there are no other (strong or soft) references to the values.

### 6. `:memento.core/soft-values`

Corresponds to `.softValues` on CacheBuilder. Boolean flag, enabling storing values using soft references.

Softly referenced objects are garbage-collected in a globally least-recently-used manner, in response to memory demand. 
Because of the performance implications of using soft references, we generally recommend using the more predictable maximum cache size instead.

### 7. `:memento.core/ttl`

Corresponds to `.expireAfterWrite` on CacheBuilder. 

Duration type property (e.g. `[15 :s]` or `15`)
 
Expire entries after the specified duration has passed since the entry was created, or the most recent replacement of the value. 
This could be desirable if cached data grows stale after a certain amount of time.

Timed expiration is performed with periodic maintenance during writes and occasionally during reads.

### 8. `:memento.core/fade`

Corresponds to `.expireAfterAccess` on CacheBuilder. 

Duration type property (e.g. `[15 :s]` or `15`).

Only expire entries after the specified duration has passed since the entry was last accessed by a read or a write. 
Note that the order in which entries are evicted will be similar to that of size-based eviction.

### 9. `:memento.core/ticker`

Corresponds to `.ticker` on CacheBuilder. 

A function of zero arguments that should return current nano time.
This is used when doing time based eviction.

The default is `(fn [] (System/nanoTime))`.

This is useful for testing and you can also make the time move in discrete amounts (e.g. you can make all
cache accesses in a request have same time w.r.t. eviction).

### 10. `:memento.core/removal-listener`

Corresponds to `.removalListener` on CacheBuilder. 

A function of three arguments, that will be called whenever an entry is removed.
The three arguments are:
- transformed key of evicted entry (after `key-fn` was applied)
- transformed result of evicted entry (after `ret-fn` was applied)
- com.google.common.cache.RemovalCause value

### 11. `:memento.core/stats`

Boolean flag, enabling collection of stats.

Corresponds to `.enableStats` on CacheBuilder. 

You can retrieve a cache's stats by using `memento.guava/stats`:

```clojure
(memento.guava/stats my-cached-function)
```

Returns `com.google.common.cache.CacheStats` instance or nil.

# Region

Guava properties when configuring a Region are the same.

The main differences:

### 1. Keys and values

Whereas in Cache, the transformed key is the argument list with `key-fn` applied to it,
the guava Region has a more complex transformed key.

The region transformed key is a vector of `[f args]` where `f` is the function that the `args`
applies to and `args` is the argument list after being processed by Cache's `key-fn` and
region's `key-fn`.

The values have had Cache's `ret-fn` and Region's `ret-fn` applied to.

### 2. `:memento.core/weight<` and `:memento.core/kv-weight` 

An alternative to `size<` for cache size based eviction, similar in behaviour as above, except:

The `:weight<` integer setting specifies max number of weight units the cache can take.

The `:kv-weight` should be a function of **3** arguments:
- **function being cached**
- transformed key
- transformed value` 

and return an integer, the number of weight units this entry should take. 

### 3. `:memento.core/removal-listener`

Corresponds to `.removalListener` on CacheBuilder. 

A function of **four** arguments, that will be called whenever an entry is removed.
The three arguments are:
- **function being cached**
- transformed key of evicted entry (after `key-fn` was applied)
- transformed result of evicted entry (after `ret-fn` was applied)
- com.google.common.cache.RemovalCause value

### 4. Fetching stats

Instead of `(memento.guava/stats my-cached-function)` you need to use
`(memento.guava/region-stats :region-id)`.
