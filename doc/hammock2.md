# Scopes

Big question is scoping of caches.

What is the use-case?

#### Two functions sharing the same cache

If two functions have same return for same input, are they not the same function?
We do not need this feature.

What about sharing the cache in terms of size? Sometimes we want to have a max size on
cache, that will affect multiple functions, rather than specify cache sizing for each function.

In that case we have a cache region, which has specific settings. One function using a region cannot
have ttl of 10 and another of 30. So the name should be enough:

```
(cache/memo :region1
  (fn [x] (+ 1 x)))
```

#### Regions

How to define the eviction properties of the region? Same as default config? Make the regions part of config?

```
(cache/set-region :region1 {:ttl 30})
```

Do we need a block variant? `cache/with-region`?

What is the general contract with regions and redefinition? Mostly we don't care. The easiest is to leave them alone.

Accessing the region for the purposes of manual eviction should be possible by naming the region or a var using the region.

#### Request scoped cache

Often you want to cache function results in the context of a request, to avoid recalculating things.
Such a cache is easy to use as it generally requires no eviction policy as it gets cleared at the end of request.

It should still provide manual eviction. 

The actual effect is that new cached values are not added to the cache outside the scope.

Should the scoped cache affect functions with normal cache operation? 

Here's a couple of options:

##### 1. if a function has a normal cache, that cache, with all the rules therein

In this case the feature is mutually exclusive with normal caching. The only downside is that here
the general cache might evict something during the request. Generally speaking staleness concerns
should be addressed by the person who defined the original cache.

##### 2. if a function has a normal cache, use that cache, but any loaded keys are not added

I have hard time imagining why you wouldn't want to save your calculated values. 

##### 3. only use scoped cache

In this implementation the request operates on basically empty cache everywhere. Cannot imagine
a usecase for this.

Should scoped cache be nestable? At this time I don't see a point, but implementing it without
nesting should not require client code changes if I decide to implement nesting later.

#### Other backend than Guava cache?

I should design with that in mind, but I don't want to explode the scope too much.
