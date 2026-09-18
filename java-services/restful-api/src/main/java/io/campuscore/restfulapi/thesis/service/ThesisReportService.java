package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brief R5: only the group leader submits the topic report. The report
 * references an external artifact — a link, an attached Word/PDF document
 * (feedback item 7), or both. There is one current version per group and it
 * is frozen once the round's GVPB grading deadline passes.
 */
@Service
@Profile("persistence")
public class ThesisReportService {

    private static final Logger log = LoggerFactory.getLogger(ThesisReportService.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisReportStorage storage;

    public ThesisReportService(NamedParameterJdbcTemplate jdbc, ThesisReportStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Transactional
    public ReportResponse submit(UUID groupId, String title, String url, String note, Jwt actor) {
        GroupContext group = loadGroup(groupId);
        requireWriteAccess(group, actor);
        requireText(url, "url");
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw invalid("url must be an http(s) link to the report document");
        }
        Instant gvpbDeadline = group.gvpbDeadline();
        if (gvpbDeadline != null && !Instant.now().isBefore(gvpbDeadline)) {
            throw conflict("REPORT_DEADLINE_PASSED",
                    "The GVPB grading deadline for this round has passed; reports are frozen");
        }
        requireApproved(groupId);
        StoredObjectRef previous = existingStoredObject(groupId);
        jdbc.update("DELETE FROM thesis.thesis_group_report WHERE group_id = :groupId",
                params().addValue("groupId", groupId));
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url, note) "
                        + "VALUES (:id, :groupId, :roundId, :submittedBy, :title, :url, :note)",
                params().addValue("id", UUID.randomUUID())
                        .addValue("groupId", groupId)
                        .addValue("roundId", group.roundId())
                        .addValue("submittedBy", studentId(actor))
                        .addValue("title", blankToNull(title, 240))
                        .addValue("url", trimmedUrl)
                        .addValue("note", blankToNull(note, 500)));
        cleanupStoredObject(previous);
        return get(groupId, actor);
    }

    /** Feedback item 7: the leader may attach the report document itself. */
    @Transactional
    public ReportResponse submitFile(UUID groupId, MultipartFile file, String title, String note, Jwt actor) {
        GroupContext group = loadGroup(groupId);
        requireWriteAccess(group, actor);
        ReportFilePolicy.ValidatedFile validated = ReportFilePolicy.validate(file);
        Instant gvpbDeadline = group.gvpbDeadline();
        if (gvpbDeadline != null && !Instant.now().isBefore(gvpbDeadline)) {
            throw conflict("REPORT_DEADLINE_PASSED",
                    "The GVPB grading deadline for this round has passed; reports are frozen");
        }
        requireApproved(groupId);
        StoredObjectRef previous = existingStoredObject(groupId);
        String storageKey = "thesis-reports/" + groupId + "/" + UUID.randomUUID() + "." + validated.extension();
        String checksum = sha256(validated.data());
        storage.put(storageKey, validated.contentType(), validated.data());
        try {
            jdbc.update("DELETE FROM thesis.thesis_group_report WHERE group_id = :groupId",
                    params().addValue("groupId", groupId));
            jdbc.update(
                    "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url, note, "
                            + "file_name, file_type, file_size, file_data, storage_provider, storage_bucket, "
                            + "storage_key, file_sha256) "
                            + "VALUES (:id, :groupId, :roundId, :submittedBy, :title, :url, :note, "
                            + ":fileName, :fileType, :fileSize, :fileData, :storageProvider, :storageBucket, "
                            + ":storageKey, :fileSha256)",
                    params().addValue("id", UUID.randomUUID())
                            .addValue("groupId", groupId)
                            .addValue("roundId", group.roundId())
                            .addValue("submittedBy", studentId(actor))
                            .addValue("title", blankToNull(title, 240))
                            .addValue("url", (String) null)
                            .addValue("note", blankToNull(note, 500))
                            .addValue("fileName", validated.fileName())
                            .addValue("fileType", validated.contentType())
                            .addValue("fileSize", validated.size())
                            // V44 remains a read-compatibility column only.
                            .addValue("fileData", (byte[]) null)
                            .addValue("storageProvider", storage.provider())
                            .addValue("storageBucket", storage.bucket())
                            .addValue("storageKey", storageKey)
                            .addValue("fileSha256", checksum));
        } catch (RuntimeException exception) {
            deleteNewObjectAfterFailure(storageKey);
            throw exception;
        }
        cleanupStoredObject(previous);
        return get(groupId, actor);
    }

    /** Serves the attached document; authorization is exactly the read matrix of {@link #get}. */
    @Transactional(readOnly = true)
    public StoredReport download(UUID groupId, Jwt actor) {
        get(groupId, actor);
        try {
            return jdbc.queryForObject(
                    "SELECT file_name, file_type, file_data, storage_provider, storage_bucket, storage_key, file_sha256 FROM thesis.thesis_group_report "
                            + "WHERE group_id = :groupId AND file_data IS NOT NULL",
                    params().addValue("groupId", groupId),
                    (rs, ignored) -> storedReportFromRow(rs));
        } catch (EmptyResultDataAccessException exception) {
            try {
                return jdbc.queryForObject(
                        "SELECT file_name, file_type, file_data, storage_provider, storage_bucket, storage_key, file_sha256 "
                                + "FROM thesis.thesis_group_report WHERE group_id = :groupId",
                        params().addValue("groupId", groupId),
                        (rs, ignored) -> storedReportFromRow(rs));
            } catch (EmptyResultDataAccessException missing) {
                throw notFound("REPORT_FILE_NOT_FOUND", "The current report is a link, not an attached document");
            }
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID groupId, Jwt actor) {
        GroupContext group = loadGroup(groupId);
        boolean member = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId(actor))) > 0;
        boolean supervisor = StringUtils.hasText(lecturerId(actor))
                && count("SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", lecturerId(actor))) > 0;
        boolean councilMember = StringUtils.hasText(lecturerId(actor))
                && group.topicId() != null
                && count("SELECT COUNT(*) FROM thesis.thesis_council_member cm "
                        + "JOIN thesis.thesis_council_topic ct ON ct.council_id = cm.council_id "
                        + "WHERE ct.topic_id = :topicId AND cm.lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", lecturerId(actor))) > 0;
        boolean admin = hasRole(actor, "ADMIN") || hasRole(actor, "TRUONG_KHOA");
        boolean lecturer = hasRole(actor, "LECTURER") || StringUtils.hasText(lecturerId(actor));
        if (!member && !supervisor && !councilMember && !admin && !lecturer) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED",
                    "Only group members, supervisors, council reviewers, lecturers, or staff can read the report");
        }
        try {
            return jdbc.queryForObject(
                    "SELECT group_id, title, url, note, submitted_by, submitted_at, updated_at, "
                            + "file_name, file_type, file_size "
                            + "FROM thesis.thesis_group_report WHERE group_id = :groupId",
                    params().addValue("groupId", groupId),
                    (rs, ignored) -> new ReportResponse(
                            UUID.fromString(rs.getString("group_id")),
                            rs.getString("title"),
                            rs.getString("url"),
                            rs.getString("note"),
                            rs.getString("submitted_by"),
                            instantOf(rs.getObject("submitted_at")),
                            instantOf(rs.getObject("updated_at")),
                            rs.getString("file_name"),
                            rs.getString("file_type"),
                            (Long) rs.getObject("file_size")));
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("REPORT_NOT_FOUND", "The group has not submitted a report yet");
        }
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listByRound(UUID roundId, Jwt actor) {
        boolean admin = hasRole(actor, "ADMIN") || hasRole(actor, "TRUONG_KHOA");
        boolean lecturer = hasRole(actor, "LECTURER") || StringUtils.hasText(lecturerId(actor));
        if (!admin && !lecturer) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_OR_STAFF_REQUIRED",
                    "Only lecturers or academic staff can access the round thesis archive");
        }
        return jdbc.query(
                "SELECT group_id, title, url, note, submitted_by, submitted_at, updated_at, "
                        + "file_name, file_type, file_size "
                        + "FROM thesis.thesis_group_report WHERE round_id = :roundId ORDER BY submitted_at DESC",
                params().addValue("roundId", roundId),
                (rs, ignored) -> new ReportResponse(
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("title"),
                        rs.getString("url"),
                        rs.getString("note"),
                        rs.getString("submitted_by"),
                        instantOf(rs.getObject("submitted_at")),
                        instantOf(rs.getObject("updated_at")),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        (Long) rs.getObject("file_size")));
    }

    @Transactional(readOnly = true)
    public ReportResponse getByTopic(UUID topicId, Jwt actor) {
        java.util.List<UUID> groupIds = jdbc.query(
                "SELECT id FROM thesis.thesis_group WHERE topic_id = :topicId AND approval_status = 'APPROVED'",
                params().addValue("topicId", topicId),
                (rs, ignored) -> UUID.fromString(rs.getString("id")));
        if (groupIds.isEmpty()) {
            return null;
        }
        try {
            return get(groupIds.get(0), actor);
        } catch (DomainException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }

    private GroupContext loadGroup(UUID groupId) {
        Map<String, Object> group = one(
                "SELECT g.id, g.round_id, g.leader_student_id, g.topic_id, r.gvpb_deadline "
                        + "FROM thesis.thesis_group g JOIN thesis.thesis_registration_round r ON r.id = g.round_id "
                        + "WHERE g.id = :groupId",
                params().addValue("groupId", groupId), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupContext(
                (UUID) group.get("id"),
                (UUID) group.get("round_id"),
                (String) group.get("leader_student_id"),
                (UUID) group.get("topic_id"),
                group.get("gvpb_deadline") == null ? null : instantOf(group.get("gvpb_deadline")));
    }

    private record GroupContext(UUID id, UUID roundId, String leaderStudentId, UUID topicId, Instant gvpbDeadline) { }

    private record StoredObjectRef(String provider, String bucket, String key) { }

    public record ReportResponse(
            UUID groupId,
            String title,
            String url,
            String note,
            String submittedBy,
            Instant submittedAt,
            Instant updatedAt,
            String fileName,
            String fileType,
            Long fileSize) { }

    /** The attached document bytes, served only through the authorized download path. */
    public record StoredReport(String fileName, String contentType, byte[] data) { }

    private void requireWriteAccess(GroupContext group, Jwt actor) {
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED",
                    "Only the group leader can submit the topic report");
        }
    }

    private void requireApproved(UUID groupId) {
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE id = :groupId AND approval_status = 'APPROVED'",
                params().addValue("groupId", groupId), Integer.class);
        if (approved == null || approved == 0) {
            throw conflict("GROUP_STATE_CONFLICT", "Reports can be submitted only after the group is approved");
        }
    }

    private StoredObjectRef existingStoredObject(UUID groupId) {
        try {
            return jdbc.queryForObject(
                    "SELECT storage_provider, storage_bucket, storage_key FROM thesis.thesis_group_report "
                            + "WHERE group_id = :groupId AND storage_key IS NOT NULL",
                    params().addValue("groupId", groupId),
                    (rs, ignored) -> new StoredObjectRef(
                            rs.getString("storage_provider"),
                            rs.getString("storage_bucket"),
                            rs.getString("storage_key")));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private StoredReport storedReportFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        byte[] data = rs.getBytes("file_data");
        String key = rs.getString("storage_key");
        if (StringUtils.hasText(key)) {
            String storedProvider = rs.getString("storage_provider");
            String storedBucket = rs.getString("storage_bucket");
            if (!storage.provider().equals(storedProvider) || !storage.bucket().equals(storedBucket)) {
                throw storageUnavailable();
            }
            try {
                data = storage.read(key);
            } catch (ThesisReportStorageException exception) {
                throw storageUnavailable();
            }
            String checksum = rs.getString("file_sha256");
            if (StringUtils.hasText(checksum) && !sha256(data).equalsIgnoreCase(checksum)) {
                throw storageUnavailable();
            }
        }
        if (data == null || data.length == 0) {
            throw notFound("REPORT_FILE_NOT_FOUND", "The current report is a link, not an attached document");
        }
        return new StoredReport(rs.getString("file_name"), rs.getString("file_type"), data);
    }

    private void cleanupStoredObject(StoredObjectRef previous) {
        if (previous == null || !storage.provider().equals(previous.provider())
                || !storage.bucket().equals(previous.bucket())) {
            return;
        }
        try {
            storage.delete(previous.key());
        } catch (ThesisReportStorageException exception) {
            // The DB row is already authoritative. Leave cleanup for an
            // operator job instead of making a successful submission look like
            // a failure because an old object was already missing.
            log.warn("Unable to remove replaced thesis report object");
        }
    }

    private void deleteNewObjectAfterFailure(String key) {
        try {
            storage.delete(key);
        } catch (ThesisReportStorageException cleanupFailure) {
            log.warn("Unable to remove a failed thesis report upload");
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static DomainException storageUnavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "REPORT_STORAGE_UNAVAILABLE",
                "The report document is temporarily unavailable");
    }

    private static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }

    private static String blankToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid("Text must contain at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private static String subject(Jwt actor) {
        return actor == null || actor.getSubject() == null ? "" : actor.getSubject();
    }

    private static String studentId(Jwt actor) {
        return normalize(actor == null ? null : actor.getClaimAsString("studentId"));
    }

    private static String lecturerId(Jwt actor) {
        return normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private static boolean hasRole(Jwt actor, String role) {
        var roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
    }

    private static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    private static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }
}
