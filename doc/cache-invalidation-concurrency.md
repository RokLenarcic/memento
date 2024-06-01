# Invalidation and loads problems

## 1. Accessing a cached value while a bulk invalidation is in progress

You might have a cached function that calls another cached function and they operate on same data, but the invalidation
is not atomic so the top function is invalidated and it immediately reconstructs an tag with invalid, but still
not removed, data from the other cached function.

This applies to tagged invalidation. 

#### Solution

Lockout/remove all the access to tagged entries of a specific tag until the invalidation is complete.

Achieved by having a map with tag IDs that are under invalidation that is atomically updated to contain the set under
invalidation, and a there are CountDownLatch objects that you can await for the invalidation to be over.

#### What to do when cached value is invalidated

If cached value is subject of ongoing invalidation pass, we need to recurse into a load, discarding the value. But 
we don't want to recurse into a load, that still starts in the middle of an invalidation pass, as that will be also discarded.

In order to minimize looping of the load function calls, we want to at least try to await the current invalidation pass.

## 2. Load completes, but an invalidation has happened during that load

Usually an invalidation in come in after a DB update or something like that. Any ongoing load will create some objects
that will have the old data, but they won't be invalidated since the invalidation happened during load.

For normal invalidation this is a simple problem, we can mark the Promise of the ongoing load as invalid, remove it from cache and retry.
But for tagged invalidations, the tag IDs of the ongoing loads are not known before the result is calculated, so we need to detect
all the tag id invalidations that happen after the load start.

So each cache instance can have a map of ongoing loads to tag IDs that were invalidated during the load. This is combined with the
lockout map to provide full coverage. 

It gets even harder when we consider caches outside the JVM like Redis.

#### Solution

On a high level we need a history of recent tag IDs invalidated and a way to tell if they pertain to the ongoing load.

We need to link those facts. So a part of the invalidation sequence would be somehow tagging every ongoing load with
invalidated tag IDs that happen during the load. The lockout map is a source of such a snapshot.