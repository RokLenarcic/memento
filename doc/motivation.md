# Motivation

Why not use `core.memoize`?

That library lacks an important feature: it does not guarantee that the function being memoized
will be called only once per each key in the presence of multiple threads accessing it.

First idea for this library was to create a `core.cache` caching provider that has that guarantee and 
pass it to `core.memoize`. But the protocol itself does not allow for a good solution.

Once I was convinced that I need to create a separate library I wanted to have more "killer" features
that `core.memoize` does not have.

## Important features

#### Execute once semantics

So the main reason for this library is that it needs to execute the memoized function once per parameter
list value, even if having concurrent calls.

It should not cache calls on exceptions.

#### Multiple eviction strategies

I've decided to use the Guava Cache and it allows similar cache eviction strategies than `core.memoize` like
`ttl`, `fifo`, size-based evicion, `lu`, `lru`. But unlike `core.cache` caches,
Guava allows multiple policies at the same time. And it also has more policies, so this is definitely an
additional killer feature.

#### Shared caches

When you have many memoized functions in the project, and each has its own cache, it is hard to predict the full
impact of your eviction policies, e.g. if you're using size based policy, it is hard to know
exactly how many cache entries there are in the system as a whole. Sometime you just want
to say `I want to cache max 10000 entries across all my memoized functions`. This is not possible
in `core.memoize`. Or you simply want one cache for most functions and a different one with high
concurrency number for a specific function. Being able to specify same cache for multiple
functions is an important feature.

#### Lexically scoped (request) cache

A lot of the time the validity of cache data has nothing to do with time or count, but you
want to get the cached number within a certain live scope such as request.

This is a very important feature.

## Nice to have features

#### Alternate keys

General memoization utilities consider all function arguments part of the key... different arguments
different entry. But sometimes some arguments don't really affect the result of the function, but rather
drive something else (like a log statement, or an error function). It would be nice to be able to specify an
alternative key function.

#### Extensibility

It would be nice to leave open access to the underlying cache object, in order to be able
to use the full power of implementation. Guava cache provides stats keeping, and other thing like
refreshes, removal listeners.

## Library Non-goals

#### Performance

It is not the goal of this library to provide an implementation of function 
memoization with a massive throughput. The use-case for this memoization is calling
external APIs or database and the performance target is to stay a couple of orders of
magnitude faster than a network access.

