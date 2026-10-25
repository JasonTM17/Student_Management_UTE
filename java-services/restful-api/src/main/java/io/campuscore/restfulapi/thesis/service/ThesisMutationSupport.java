package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.model.RoundType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Shared, dependency-free helpers of the thesis mutation services: actor-claim
 * readers, text/window validation, the group-size band, the DomainException
 * factories, and the post-commit runner. One implementation shared by the
 * facade and every extracted service — the single-implementation rule the
 * service split is built on.
 */
final class ThesisMutationSupport {

    static final int MIN_GROUP_MEMBERS = 3;
    static final int MAX_GROUP_MEMBERS = 5;

    private ThesisMutationSupport() {
    }

    static String subject(Jwt actor) {
        return actor == null || actor.getSubject() == null ? "" : actor.getSubject();
    }

    static String studentId(Jwt actor) {
        return normalize(actor == null ? null : actor.getClaimAsString("studentId"));
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
    }

    static void requireText(String value, String name, int maxLength) {
        requireText(value, name);
        if (value.trim().length() > maxLength) throw invalid(name + " must contain at most " + maxLength + " characters");
    }

    static void requireRoundWindow(Instant start, Instant end, String startField, String endField, String rule) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw scheduleOrder(rule, endField + " must be after " + startField);
        }
    }

    static DomainException scheduleOrder(String rule, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "THESIS_ROUND_SCHEDULE_ORDER", message,
                Map.of("rule", rule));
    }

    static String requireReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.isBlank() || reason.length() > 500) {
            throw invalid("reason is required and must contain at most 500 characters");
        }
        return reason;
    }

    static void requireGovernanceRole(Jwt actor) {
        if (!isAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GOVERNANCE_ROLE_REQUIRED",
                    "This action requires an admin or faculty head role");
        }
    }

    static RoundType requireRoundType(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("thesisType is required");
        }
        try {
            return RoundType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("thesisType must be one of MON_HOC, NCKH, TLCN, KLTN");
        }
    }

    static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    /**
     * Binds an {@link Instant} to JDBC. The PostgreSQL driver cannot infer a SQL type for
     * {@code java.time.Instant} ("Can't infer the SQL type to use for an instance of
     * java.time.Instant"), which previously made {@code POST /thesis/rounds} fail with HTTP 500.
     * The rest of the codebase binds timestamps as {@link java.sql.Timestamp} (see
     * RegistrationService), so we follow the same convention here.
     */
    static java.sql.Timestamp tsOf(Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
    }

    static boolean isAdmin(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN") || roles.contains("TRUONG_KHOA"));
    }

    static boolean isLecturer(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("LECTURER");
    }

    static boolean isGlobalAdmin(Jwt actor) {
        return hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN");
    }

    static boolean hasRole(Jwt actor, String role) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    static boolean isProgressStatus(GroupStatus status) {
        return status == GroupStatus.DRAFT || status == GroupStatus.SUBMITTED
                || status == GroupStatus.COMPLETED || status == GroupStatus.CANCELLED;
    }

    /**
     * Runs the fan-out only after the mutation commits: the notifier methods
     * run REQUIRES_NEW, so an in-transaction call would still publish a
     * notification for a change a later rollback erased.
     */
    static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }
}
