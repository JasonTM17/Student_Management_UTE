package io.campuscore.restfulapi.system.service;

import io.campuscore.restfulapi.system.repository.DatabaseProbeRepository;
import org.springframework.stereotype.Service;

/**
 * Readiness check behind the health endpoint: runs the probe statement and
 * lets a {@code DataAccessException} propagate so the caller reports DOWN.
 */
@Service
public class DatabaseReadinessService {

    private final DatabaseProbeRepository probe;

    public DatabaseReadinessService(DatabaseProbeRepository probe) {
        this.probe = probe;
    }

    /** Returns once the probe statement succeeds; throws when the database is unreachable. */
    public void checkReady() {
        probe.probe();
    }
}
