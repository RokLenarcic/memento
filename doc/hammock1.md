# Use stories

A pile of use examples that will drive the design.

#### Caching a function

```
(defmemofn factorize
  "Doc str"
  {::cache/ttl 30}
  (inc 1))
```

Q: What about recursive calls, should they work?

A: Not for the same parameters, that would have to be a neat trick.

Q: If namespace is reloaded, should it retain the cache?

A: Hm, maybe defer to whether the user uses `defonce` to define the function variable.

I don't want to cover all the different ways that users can define a function and all the
custom function/symbol defining macros.

Better to use alter-var-root? 

```
(defn inc [x] (+ 1 x))
(alter-var-root #'x cache/memo {:ttl 30})
```

This is nice and explicit, but a bit ugly. People love a nice interface.
Plus the issue here is that we never get the name of the symbol, so we cannot use it in a key.
Does it work with the recursion? (Test this!) On the bright side it can cache a local function similarly.

```
(defn inc [x] (+ 1 x))
(cache/memo {:ttl 30} inc) 
```

Check if having the symbol is useful. And see if this presents some sort downside (except explicitness)
to `alter-var-root`.

#### Reloading namespaces

In most systems we don't care one way or the other. In production there's generally no reloading, 
so reloading semantics can be whatever is the easiest, as long as it's consistent. In development, reloading
is a powerful tool, but this is a caching library which makes it less important if it retains the cache in dev
or not. 

What about the technical aspects? We know reloading a Protocol makes the classes not match (and fail equals),
and there might be other hidden bugs and regressions when keys contain classes. Keeping generated IFn classes
in caches also represents a type of memory leak. This needs to be tested and regression tests need to be kept.

#### Memoizing a local function 

```
(cache/memo {:ttl 30}
  (fn [x] (+ 1 x)))
```

The cache key is the function instance? What about function class? Some kind of static scope caching for
local functions?

```
(cache/memo {:ttl 30 :static true}
  (fn [x] (+ 1 x)))
```

Just cache by class rather than instance.

#### Hit?

`core.memoize` will memoize any non-exceptional result. Sometimes we don't want to memoize a `nil`. For instance
if a function returns some analysis results or nil by ID, we might be checking something that's not there yet, but it will
be, so we don't want to memoize nil. Similarly, some exceptions might suggests that a certain parameter list is always
wrong, so we should cache that i.e. `Long.parseLong("X");` will always throw an exception. We need to provide 
a way for user to specify hit/or/miss.

We could check if function returns a 2-piece vector with a namespaced keyword. We should let user specify a function
that processes this. `::cache/hit` `::cache/miss`, `::cache/entry` (is a hit, a pair of k,v)

#### Defaults

Can the user set a different default caching than pure memoize? There should be a way to set defaults.

```
cache/*defaults*
```

#### Dev/testing

There should be a centralized cache disable option. Should cache read the option at every access?
If it were free, I'd say yes, but in general you're either running with cache enabled or disabled, so 
that makes it a start option. There should also be java system property, to avoid having to have code disabling it.

1. load the system property into defaults
2. every time a new cache is requested look at the value
3. provide a convenience function for setting the value

#### Keys

There should be a function that allows one to modify the key being used. This is important, because sometimes
a function has a live parameter like a db-connection that we don't want to be in the caching key. Other times
we want extra things in key from function context e.g. we know function return varies by the current user.

A neat thing we might be adding a format like `[_ % % % _]` to describe in short `drop first and fifth argument from the key`.


