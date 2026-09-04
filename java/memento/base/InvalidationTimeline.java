package memento.base;

import clojure.lang.IPersistentMap;
import clojure.lang.PersistentHashMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * History for coordinating operations with invalidations whose identifiers are
 * discovered only when an operation completes.
 *
 * <p>An {@link Operation} retains the timeline history appended after it. Release all references
 * to the handle as soon as the operation finishes or becomes irrelevant so the JVM can reclaim
 * obsolete history.</p>
 */
public final class InvalidationTimeline {

    /** Opaque start point retained by an operation. */
    public interface Operation {}

    /** Opaque state returned when an invalidation starts. End it exactly once. */
    public static final class Invalidation {
        private final InvalidationTimeline owner;
        private final Set<?> ids;

        private Invalidation(InvalidationTimeline owner, Set<?> ids) {
            this.owner = owner;
            this.ids = ids;
        }

        /** Return the immutable identifiers supplied when this invalidation started. */
        public Set<?> ids() {
            return ids;
        }
    }

    private static final long DEFAULT_QUIESCENCE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Object appendLock = new Object();
    private final long quiescenceTimeoutNanos;
    private volatile Node tail;

    public InvalidationTimeline() {
        this.quiescenceTimeoutNanos = DEFAULT_QUIESCENCE_TIMEOUT_NANOS;
        tail = new Node(this, PersistentHashMap.EMPTY, Collections.emptySet());
    }

    /** Create a timeline with a custom wait timeout, primarily for backend use and testing. */
    public InvalidationTimeline(long quiescenceTimeout, TimeUnit unit) {
        if (quiescenceTimeout <= 0) {
            throw new IllegalArgumentException("Quiescence timeout must be positive");
        }
        this.quiescenceTimeoutNanos = unit.toNanos(quiescenceTimeout);
        if (quiescenceTimeoutNanos <= 0) {
            throw new IllegalArgumentException("Quiescence timeout is too small");
        }
        tail = new Node(this, PersistentHashMap.EMPTY, Collections.emptySet());
    }

    /** Capture the start of an operation. */
    public Operation startOperation() {
        return tail;
    }

    /** Publish the start of an invalidation and return the state required to end it. */
    public Invalidation startInvalidation(Iterable<?> ids) {
        Set<Object> result = new HashSet<>();
        for (Object id : ids) {
            result.add(id);
        }
        if (!result.isEmpty()) {
            append(result, true);
        }
        return new Invalidation(this, result);
    }

    /** Publish the end of an invalidation. Each handle must be ended exactly once. */
    public void endInvalidation(Invalidation invalidation) {
        requireOwner(invalidation);
        if (!invalidation.ids.isEmpty()) {
            append(invalidation.ids, false);
        }
    }

    /**
     * Return whether any supplied identifier was already being invalidated when the operation
     * started, or began invalidation before this call's timeline snapshot.
     */
    public boolean invalidated(Operation operation, Set<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return invalidatedBetween(requireNode(operation), tail, ids);
    }

    /** Return whether any supplied identifier is currently being invalidated. */
    public boolean hasActiveInvalidation(Set<?> ids) {
        return ids != null && !ids.isEmpty() && hasActiveInvalidation(tail, ids);
    }

    /**
     * Block until no supplied identifier is being invalidated, then return.
     *
     * <p>Callers retrying an operation that lost to an invalidation use this instead of spinning:
     * a lockout is held open for the duration of the caller's underlying write, so an immediate
     * retry would re-lose for as long as that write takes.</p>
     *
     * <p>Waiting fails after one minute by default so a leaked or self-owned lockout produces
     * a diagnosable exception instead of an indefinite hang. Waiters are also interruptible.</p>
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public void awaitQuiescent(Set<?> ids) throws InterruptedException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        long startedAt = System.nanoTime();
        synchronized (appendLock) {
            while (hasActiveInvalidation(tail, ids)) {
                long remaining = quiescenceTimeoutNanos - (System.nanoTime() - startedAt);
                if (remaining <= 0) {
                    throw new IllegalStateException(
                            "Secondary-ID invalidation did not complete within "
                            + TimeUnit.NANOSECONDS.toMillis(quiescenceTimeoutNanos)
                            + " milliseconds; the completion may be leaked or waiting on its own lockout: "
                            + ids);
                }
                long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
                int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
                appendLock.wait(millis, nanos);
            }
        }
    }

    private boolean hasActiveInvalidation(Node snapshot, Set<?> ids) {
        for (Object id : ids) {
            if (snapshot.activeIds.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean invalidatedBetween(Node start, Node finish, Set<?> ids) {
        if (hasActiveInvalidation(start, ids)) {
            return true;
        }
        Node node = start;
        while (node != finish) {
            node = node.next;
            if (node == null) {
                throw new IllegalStateException("Timeline finish does not follow its start");
            }
            if (intersects(node.startedIds, ids)) {
                return true;
            }
        }
        return false;
    }

    private Node append(Set<?> ids, boolean starting) {
        synchronized (appendLock) {
            Node predecessor = tail;
            IPersistentMap activeIds = updateActiveIds(predecessor.activeIds, ids, starting);
            Node node = new Node(this, activeIds, starting ? ids : Collections.emptySet());
            predecessor.next = node;
            // The volatile tail write publishes the node and its link to lock-free readers.
            tail = node;
            if (!starting) {
                // A lockout was released; let awaitQuiescent waiters re-test.
                appendLock.notifyAll();
            }
            return node;
        }
    }

    private Node requireNode(Operation operation) {
        if (!(operation instanceof Node) || ((Node) operation).owner != this) {
            throw new IllegalArgumentException("Operation belongs to a different timeline");
        }
        return (Node) operation;
    }

    private void requireOwner(Invalidation invalidation) {
        if (invalidation == null || invalidation.owner != this) {
            throw new IllegalArgumentException("Invalidation belongs to a different timeline");
        }
    }

    private static IPersistentMap updateActiveIds(IPersistentMap activeIds, Set<?> ids,
                                                   boolean starting) {
        IPersistentMap updated = activeIds;
        for (Object id : ids) {
            long count = ((Number) updated.valAt(id, 0L)).longValue();
            if (starting) {
                updated = updated.assoc(id, count + 1);
            } else if (count <= 1) {
                updated = updated.without(id);
            } else {
                updated = updated.assoc(id, count - 1);
            }
        }
        return updated;
    }

    private static boolean intersects(Set<?> left, Set<?> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.size() <= right.size()) {
            for (Object id : left) {
                if (right.contains(id)) {
                    return true;
                }
            }
        } else {
            for (Object id : right) {
                if (left.contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Node implements Operation {
        private final InvalidationTimeline owner;
        private final IPersistentMap activeIds;
        private final Set<?> startedIds;
        private Node next;

        private Node(InvalidationTimeline owner, IPersistentMap activeIds, Set<?> startedIds) {
            this.owner = owner;
            this.activeIds = activeIds;
            this.startedIds = startedIds;
        }
    }
}
