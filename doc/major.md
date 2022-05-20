# Caches and memoize calls

### Intro

We've seen `defmemo` macro that defines a function, creates a cache based on conf, then attaches that cache.
It is a combination of a `defn` to define a function and a `memento.core/memo` call to create a cache and bind it.

And `memo` itself is a combination of:

- creating a Cache via `memento.core/create` (optional, as you can use an existing cache)
- binding the cache to the function via `memento.core/bind` (a MountPoint is used to connect a function being memoized to the cache)

When `memo` or equivalent is called with a conf map, a new cache will be created and bound, if a `memento.base/Cache` instance
is given that will be used instead of creating a new cache from a conf map. After `memo` or `bind` the function has a MountPoint attached. 

#### Creating a cache

Creating a cache is done by using `memento.core/create`, which takes a map of configuration (called **cache conf**).
You can use the resulting Cache with multiple functions. The configuration properties (map keys) can be found
in `memento.config` and `memento.caffeine.config`, look for "Cache setting" in docstring.

If `memento.config/enabled?` is false, this function always returns `memento.base/no-cache`, which is a Cache
implementation that doesn't do any caching. You can set this at start-up by specifying java property:
`-Dmemento.enabled=false` which globally disables caching.

#### Binding the cache

Binding the cache to a function is done by `memento.core/bind`. Parameters are:

- a fn or a var, if var, the root value of var is changed to a memoized version
- a mount point configuration or **mount conf** for short
- a Cache instance that you want to bind

Mount conf is either a map of mount point configuration properties, or a shorthand (see below).
The configuration properties (map keys) can be found in `memento.config`, look for "function bind" in docstring.

Instead of map of properties, **mount conf** can be a shorthand, which has the following two shorthands:
- `[:some-keyword :another-keyword]` -> `{:memento.core/tags [:some-keyword :another-keyword]}`
- `:a-keyword` -> `{:memento.core/tags [:a-keyword]}`

#### Create + bind combined

You can combine both functions into 1 call using `memento.core/memo`.

```clojure
(m/memo fn-or-var mount-conf cache-conf)
```

To make things shorter, there's a 2-arg variant that allows that you specify both configurations at once:

```clojure
(m/memo fn-or-var conf)
```

If conf is a map, then all the properties valid for mount conf are treated as such. The rest is passed to cache create.
If conf is a mount conf shorthand then cache conf is considered to be {}. E.g.

```clojure
(m/memo my-fn :my-tag)
```

This creates a memoized function tagged with `:my-tag` bound to a cache that does no caching.
