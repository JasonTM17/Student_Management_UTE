package io.campuscore.restfulapi.thesis.assistant.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.campuscore.restfulapi.thesis.assistant.service.PrerequisiteMapSyncService;

/**
 * Regenerates the bilingual prerequisite-map documents (V97) from the LIVE
 * {@code academic."CourseRequirement"} rows whenever they drift. The V97 map
 * is a migration-time snapshot: later requirement changes or course renames
 * would otherwise leave the assistant answering from a stale map while the
 * registration gate enforces the new truth.
 *
 * <p>Daily the job recomputes the map bodies from the same string_agg the
 * migration uses, compares them against the active runtime documents, and on
 * drift archives the current PUBLISHED revisions, appends reviewed ones, and
 * promotes a fresh MANUAL release via {@code SqlKnowledgeReleasePromoter} —
 * the same governed path the admin knowledge UI uses, so runtime hash
 * validation keeps passing. When the active release is owned by another
 * authority (Supabase), the promoter refuses and the job only logs.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.prerequisite-map-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PrerequisiteMapSyncJob {
    private static final Logger log = LoggerFactory.getLogger(PrerequisiteMapSyncJob.class);

    private final PrerequisiteMapSyncService sync;

    public PrerequisiteMapSyncJob(PrerequisiteMapSyncService sync) {
        this.sync = sync;
    }

    /** Runs daily at 04:12 server time (off-peak, fixed schedule, no overlap). */
    @Scheduled(cron = "${assistant.prerequisite-map-sync.cron:0 12 4 * * *}")
    public void synchronizeMapDocuments() {
        try {
            int updated = sync.synchronizeMapDocuments();
            if (updated > 0) {
                log.info("PREREQUISITE_MAP_SYNC regenerated {} document(s) from live CourseRequirement rows", updated);
            }
        } catch (Exception exception) {
            // Telemetry job: a failed sync must never break the assistant.
            log.warn("PREREQUISITE_MAP_SYNC failed: {}: {}",
                    exception.getClass().getSimpleName(), exception.getMessage());
        }
    }
}
