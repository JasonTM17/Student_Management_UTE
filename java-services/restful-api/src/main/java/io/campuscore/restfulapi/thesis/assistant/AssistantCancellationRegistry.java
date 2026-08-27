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
    private final ConcurrentHashMap<Key, Handle> handles = new ConcurrentHashMap<>();

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
        }
    }

    private static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
    }

    private record Key(String ownerId, UUID clientRequestId, long leaseGeneration) { }
}
