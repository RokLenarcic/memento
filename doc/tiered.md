# Tiered caching

You can use caches that combine two other caches in some way. The easiest way to generate
the cache configuration needed is to use `memento.core/tiered`,`memento.core/consulting`, `memento.core/daisy`.

Each of these takes two parameters:
- the "local" cache
- the "upstream" cache

These parameters can be existing `Cache` instances or cache configuration maps (in which case a new Cache will
be created.)

Invalidation operations on these combined caches also affect upstream. Other operations only affect local cache.

### memento.core/tiered

This cache works like CPU cache would.

Entry is fetched from cache, delegating to upstream is not found. After the operation
the entry is in both caches.

Useful when upstream is a big cache that outside the JVM, but it's not that inexpensive, so you
want a local smaller cache in front of it.

### memento.core/consulting

Entry is fetched from cache, if not found, the upstream is asked for entry if present (but not to make one
in the upstream).

After the operation, the entry is in local cache, upstream is unchanged.

Useful when you want to consult a long term upstream cache for existing entries, but you don't want any
entries being created for the short term cache to be pushed upstream.

This is what you usually want when you put a request scoped cache in front of an existing longer cache.

```clojure
(m/with-caches :request (memoize #(m/create (m/consulting inf-cache %)))
  ....)
```

In this kind of setup, the request scoped cache will use any entries on any caches that were
on functions outside this block, but it will not introduce more entries to them.

### memento.core/daisy

Entry is returned from cache IF PRESENT, otherwise upstream is hit. The returned value
is NOT added to cache.

After the operation the entry is either in local or upstream cache.

Useful when you don't want entries from upstream accumulating in local
cache, and you're feeding the local cache via some other means:
- a preloaded fixed cache
- manually adding entries
