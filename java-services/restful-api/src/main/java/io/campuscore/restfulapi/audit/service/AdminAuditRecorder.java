package io.campuscore.restfulapi.audit.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import org.springframework.stereotype.Service;
import io.campuscore.restfulapi.audit.repository.AdminAuditWriteRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADM-P0-3: records destructive or privilege-affecting administrative operations.
 *
 * <p>Announcements already had an audit table, so the gap was never "we do not know
 * how" — it was that nothing else was wired to the idea. User creation, role
 * changes, password resets, hard deletes, catalog deletion and enrollment deletion
 * left no trace at all, and an enrollment delete also removes that student's grade
 * history.
 *
 * <p>Written with {@link Propagation#MANDATORY}: the record must join the caller's
 * transaction so a mutation and its audit row commit or roll back together. If this
 * ran in its own transaction, a rolled-back delete would still leave an audit row
 * claiming it happened, and a committed delete could lose its row — both worse than
 * no audit at all, because they are wrong rather than absent.
 *
 * <p>Credential redaction lives here rather than at the call sites. A rule that
 * every future caller must remember is a rule that will eventually be forgotten,
 * and the one thing this table must never contain is a working credential.
 */
@Service
@Profile("persistence")
public class AdminAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditRecorder.class);

    /**
     * Keys whose values are removed before serialization. Matched case-insensitively
     * and by substring, so `password`, `passwordHash`, `temporaryPassword` and
     * `currentPassword` are all covered by the single entry.
     */
    private static final List<String> REDACTED_KEY_FRAGMENTS = List.of("password", "secret", "token");

    private static final String REDACTED = "[redacted]";

    private final AdminAuditWriteRepository repository;

    public AdminAuditRecorder(AdminAuditWriteRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            String actorId,
            String actorLabel,
            String action,
            String entityType,
            String entityId,
            String summary) {
        record(actorId, actorLabel, action, entityType, entityId, summary, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            String actorId,
            String actorLabel,
            String action,
            String entityType,
            String entityId,
            String summary,
            Object beforeState,
            Object afterState) {
        repository.insert(actorId, truncate(actorLabel, 240), action, entityType, entityId,
                truncate(summary, 500), serialize(beforeState), serialize(afterState));
    }

    /** Convenience for the common "delete this record" case. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDeletion(
            String actorId, String actorLabel, String entityType, String entityId, Object beforeState) {
        record(
                actorId,
                actorLabel,
                "DELETED",
                entityType,
                entityId,
                entityType + " " + entityId + " deleted by " + describeActor(actorLabel, actorId),
                beforeState,
                null);
    }

    private static String describeActor(String actorLabel, String actorId) {
        if (actorLabel != null && !actorLabel.isBlank()) {
            return actorLabel;
        }
        return actorId == null || actorId.isBlank() ? "an automated actor" : actorId;
    }

    /**
     * Serializes the state snapshot after removing credentials. A JSON-ish map is
     * produced without a JSON dependency so the recorder stays usable from any
     * service without pulling one in.
     */
    static String serialize(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                safe.put(key, isCredentialKey(key) ? REDACTED : entry.getValue());
            }
            return safe.toString();
        }
        return String.valueOf(state);
    }

    private static boolean isCredentialKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return REDACTED_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
