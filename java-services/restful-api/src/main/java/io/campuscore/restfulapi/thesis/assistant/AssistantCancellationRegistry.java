package io.campuscore.restfulapi.thesis.assistant;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Single-instance provider handle fence for owner-scoped cancellation. */
@Component
@Profile("persistence")
public class AssistantCancellationRegistry {
    /** Generation zero is reserved for the pre-reservation disconnect fence. */
    static final long PRE_RESERVATION_GENERATION = 0L;
    private final ConcurrentHashMap<Key, Handle> handles = new ConcurrentHashMap<>();

    public void preCancel(String ownerId, UUID clientRequestId) {
        fence(ownerId, clientRequestId, PRE_RESERVATION_GENERATION);
    }

    public boolean isPreCancelled(String ownerId, UUID clientRequestId) {
        Handle handle = handles.get(new Key(ownerId, clientRequestId, PRE_RESERVATION_GENERATION));
        return handle != null && handle.cancelled.get();
    }

    /** Clears the in-process marker after the durable ledger tombstone wins. */
    public void clearPreCancel(String ownerId, UUID clientRequestId) {
        handles.remove(new Key(ownerId, clientRequestId, PRE_RESERVATION_GENERATION));
    }

    public AtomicBoolean register(String ownerId, UUID clientRequestId, long leaseGeneration) {
        // Keep a cancellation fence even when the cancel CAS wins in the small
        // gap before the provider thread registers its transport handle.
        return handles.computeIfAbsent(new Key(ownerId, clientRequestId, leaseGeneration), ignored -> new Handle()).cancelled;
    }

    public boolean cancel(String ownerId, UUID clientRequestId, long leaseGeneration) {
        Handle handle = handles.get(new Key(ownerId, clientRequestId, leaseGeneration));
        if (handle == null) return false;
        synchronized (handle) {
            return handle.cancelled.compareAndSet(false, true);
        }
    }

    /** Installs a terminal fence when the database CAS wins before registration. */
    public void fence(String ownerId, UUID clientRequestId, long leaseGeneration) {
        Handle handle = handles.computeIfAbsent(new Key(ownerId, clientRequestId, leaseGeneration), ignored -> new Handle());
        synchronized (handle) {
            handle.cancelled.set(true);
        }
    }

    /**
     * Serializes the terminal fence with the actual transport emission. Once a
     * database CAS has won and calls {@link #fence}, no later callback can pass
     * this gate and publish a delta/citation for the fenced generation.
     */
    public boolean emitIfActive(String ownerId, UUID clientRequestId, long leaseGeneration, Runnable emission) {
        Handle handle = handles.get(new Key(ownerId, clientRequestId, leaseGeneration));
        if (handle == null) return false;
        synchronized (handle) {
            if (handle.cancelled.get()) return false;
            emission.run();
            return true;
        }
    }

    public void remove(String ownerId, UUID clientRequestId, long leaseGeneration) {
        Key key = new Key(ownerId, clientRequestId, leaseGeneration);
        Handle handle = handles.get(key);
        if (handle == null) return;
        synchronized (handle) {
            handle.cancelled.set(true);
            handles.remove(key, handle);
            // A worker that reached terminal cleanup also clears the
            // generation-zero marker installed by an earlier disconnect.
            handles.remove(new Key(ownerId, clientRequestId, PRE_RESERVATION_GENERATION));
        }
    }

    private static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
    }

    private record Key(String ownerId, UUID clientRequestId, long leaseGeneration) { }
}
