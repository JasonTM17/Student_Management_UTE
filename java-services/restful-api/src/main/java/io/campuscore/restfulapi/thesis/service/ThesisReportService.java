package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ReportUpdateRequest;
import io.campuscore.restfulapi.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.campuscore.restfulapi.thesis.config.ThesisReportStorageProperties;
import io.campuscore.restfulapi.thesis.storage.LocalThesisReportStorage;
import io.campuscore.restfulapi.thesis.storage.ReportFilePolicy;
import io.campuscore.restfulapi.thesis.storage.ThesisReportStorage;
import io.campuscore.restfulapi.thesis.storage.ThesisReportStorageException;

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
    private final ThesisReportStorageProperties storageProperties;
    private final ThesisGroupReadRepository groups;

    public ThesisReportService(NamedParameterJdbcTemplate jdbc, ThesisReportStorage storage,
            ThesisReportStorageProperties storageProperties, ThesisGroupReadRepository groups) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.groups = groups;
    }

    @Transactional
    public ReportResponse submit(UUID groupId, String title, String url, String note, Jwt actor) {
        GroupContext group = lockGroupForWrite(groupId);
        requireWriteAccess(group, actor);
        requireText(url, "url");
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw invalid("url must be an http(s) link to the report document");
        }
        requireReportWritable(group);
        requireApproved(groupId);
        // Upsert: the first submission INSERTs the row and every later edit
        // UPDATEs it in place. The attached document columns, submission
        // identity (submitted_by) and timestamp (submitted_at) are never
        // destroyed by a link/metadata edit, and no storage object is deleted
        // on this path because no file is replaced.
        int updated = jdbc.update(
                "UPDATE thesis.thesis_group_report SET "
                        + "title = CASE WHEN :titlePresent THEN :title ELSE title END, "
                        + "url = :url, "
                        + "note = CASE WHEN :notePresent THEN :note ELSE note END, "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE group_id = :groupId",
                params().addValue("groupId", groupId)
                        .addValue("titlePresent", title != null)
                        .addValue("title", blankToNull(title, 240))
                        .addValue("url", trimmedUrl)
                        .addValue("notePresent", note != null)
                        .addValue("note", blankToNull(note, 500)));
        if (updated == 0) {
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
        }
        return get(groupId, actor);
    }

    /** Feedback item 7: the leader may attach the report document itself. */
    @Transactional
    public ReportResponse submitFile(UUID groupId, MultipartFile file, String title, String note, Jwt actor) {
        // Pre-lock preview: cheap authorization/shape checks run before any
        // network I/O so a rejected upload never touches storage.
        GroupContext preview = loadGroup(groupId);
        requireWriteAccess(preview, actor);
        requireReportWritable(preview);
        requireApproved(groupId);
        ReportFilePolicy.ValidatedFile validated = ReportFilePolicy.validate(file);
        // The database provider keeps bytes in file_data instead of an object
        // store, so no storage_key is written and put() is not needed.
        boolean inDatabase = "database".equals(storage.provider());
        String storageKey = inDatabase
                ? null
                : "thesis-reports/" + groupId + "/" + UUID.randomUUID() + "." + validated.extension();
        String checksum = sha256(validated.data());
        boolean cleanupOnRollback = registerNewObjectRollbackCleanup(storageKey);
        StoredObjectRef previous;
        try {
            // The object upload must NOT hold the group/round row locks: a
            // slow provider call inside lockGroupForWrite used to serialize
            // every other mutation in the round for the full transfer. The
            // locks are taken after the bytes land; the post-lock checks then
            // re-verify window and approval against the freshest state.
            if (!inDatabase) {
                storage.put(storageKey, validated.contentType(), validated.data());
            }
            GroupContext group = lockGroupForWrite(groupId);
            requireReportWritable(group);
            requireApproved(groupId);
            previous = existingStoredObject(groupId);
            // Upsert: replace only the file/storage/checksum columns. A prior
            // link (url), the submission identity and submitted_at survive an
            // upload; title/note are overwritten only when the caller sent a
            // new value.
            int updated = jdbc.update(
                    "UPDATE thesis.thesis_group_report SET "
                            + "title = CASE WHEN :titlePresent THEN :title ELSE title END, "
                            + "note = CASE WHEN :notePresent THEN :note ELSE note END, "
                            + "file_name = :fileName, file_type = :fileType, file_size = :fileSize, "
                            + "file_data = :fileData, storage_provider = :storageProvider, "
                            + "storage_bucket = :storageBucket, storage_key = :storageKey, "
                            + "file_sha256 = :fileSha256, updated_at = CURRENT_TIMESTAMP "
                            + "WHERE group_id = :groupId",
                    params().addValue("groupId", groupId)
                            .addValue("titlePresent", title != null)
                            .addValue("title", blankToNull(title, 240))
                            .addValue("notePresent", note != null)
                            .addValue("note", blankToNull(note, 500))
                            .addValue("fileName", validated.fileName())
                            .addValue("fileType", validated.contentType())
                            .addValue("fileSize", validated.size())
                            .addValue("fileData", inDatabase ? validated.data() : (byte[]) null)
                            .addValue("storageProvider", storage.provider())
                            .addValue("storageBucket", storage.bucket())
                            .addValue("storageKey", storageKey)
                            .addValue("fileSha256", checksum));
            if (updated == 0) {
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
                                // V44-era column doubles as the durable store for
                                // the database provider; object providers keep it null.
                                .addValue("fileData", inDatabase ? validated.data() : (byte[]) null)
                                .addValue("storageProvider", storage.provider())
                                .addValue("storageBucket", storage.bucket())
                                .addValue("storageKey", storageKey)
                                .addValue("fileSha256", checksum));
            }
        } catch (RuntimeException exception) {
            if (!cleanupOnRollback) {
                deleteNewObjectAfterFailure(storageKey);
            }
            throw exception;
        }
        // The previous object is replaced only when the row actually carried a
        // storage object; the deletion stays deferred to afterCommit.
        cleanupStoredObject(previous);
        return get(groupId, actor);
    }

    /**
     * Non-destructive metadata/link edit. Field-absent means "keep the stored
     * value": a record attribute cannot distinguish an absent JSON key from a
     * JSON null, so only {@code clearUrl} performs the explicit link removal —
     * and only while a file artifact remains, keeping the {@code
     * ck_report_artifact} invariant intact.
     */
    @Transactional
    public ReportResponse updateReport(UUID groupId, ReportUpdateRequest request, Jwt actor) {
        GroupContext group = lockGroupForWrite(groupId);
        requireWriteAccess(group, actor);
        requireReportWritable(group);
        requireApproved(groupId);
        Map<String, Object> row = one(
                "SELECT url, storage_key, file_data IS NOT NULL AS has_file_data "
                        + "FROM thesis.thesis_group_report WHERE group_id = :groupId",
                params().addValue("groupId", groupId), "REPORT_NOT_FOUND", "The group has not submitted a report yet");
        boolean hasFileArtifact = row.get("storage_key") != null || Boolean.TRUE.equals(row.get("has_file_data"));
        String newUrl = (String) row.get("url");
        boolean requestSetsUrl = request != null && request.url() != null;
        boolean requestClearsUrl = request != null && Boolean.TRUE.equals(request.clearUrl());
        if (requestSetsUrl && requestClearsUrl) {
            throw invalid("url and clearUrl cannot be combined");
        }
        if (requestSetsUrl) {
            String trimmedUrl = request.url().trim();
            if (trimmedUrl.isEmpty()
                    || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                throw invalid("url must be an http(s) link to the report document");
            }
            newUrl = trimmedUrl;
        } else if (requestClearsUrl) {
            if (!hasFileArtifact) {
                throw conflict("REPORT_ARTIFACT_REQUIRED",
                        "A report must keep at least one artifact; attach a document before removing the link");
            }
            newUrl = null;
        }
        boolean titlePresent = request != null && request.title() != null;
        boolean notePresent = request != null && request.note() != null;
        jdbc.update(
                "UPDATE thesis.thesis_group_report SET "
                        + "title = CASE WHEN :titlePresent THEN :title ELSE title END, "
                        + "note = CASE WHEN :notePresent THEN :note ELSE note END, "
                        + "url = CASE WHEN :urlPresent THEN :url ELSE url END, "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE group_id = :groupId",
                params().addValue("groupId", groupId)
                        .addValue("titlePresent", titlePresent)
                        .addValue("title", titlePresent ? blankToNull(request.title(), 240) : null)
                        .addValue("notePresent", notePresent)
                        .addValue("note", notePresent ? blankToNull(request.note(), 500) : null)
                        .addValue("urlPresent", requestSetsUrl || requestClearsUrl)
                        .addValue("url", newUrl));
        return get(groupId, actor);
    }

    /**
     * Detaches the stored document while keeping the link artifact. The file
     * columns are nulled in the same UPDATE and the object itself is deleted
     * afterCommit; refusing when no link remains keeps the report legal under
     * {@code ck_report_artifact}.
     */
    @Transactional
    public ReportResponse removeReportFile(UUID groupId, Jwt actor) {
        GroupContext group = lockGroupForWrite(groupId);
        requireWriteAccess(group, actor);
        requireReportWritable(group);
        requireApproved(groupId);
        Map<String, Object> row = one(
                "SELECT url FROM thesis.thesis_group_report WHERE group_id = :groupId",
                params().addValue("groupId", groupId), "REPORT_NOT_FOUND", "The group has not submitted a report yet");
        if (row.get("url") == null) {
            throw conflict("REPORT_ARTIFACT_REQUIRED",
                    "A report must keep at least one artifact; attach a link before removing the document");
        }
        StoredObjectRef previous = existingStoredObject(groupId);
        jdbc.update(
                "UPDATE thesis.thesis_group_report SET "
                        + "file_name = NULL, file_type = NULL, file_size = NULL, file_data = NULL, "
                        + "storage_provider = NULL, storage_bucket = NULL, storage_key = NULL, "
                        + "file_sha256 = NULL, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE group_id = :groupId",
                params().addValue("groupId", groupId));
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
        boolean admin = hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN");
        boolean dean = !admin && hasRole(actor, "TRUONG_KHOA");
        boolean facultyReader = dean && groups.groupInFaculty(groupId, requireDeanFaculty(actor));
        boolean lecturer = !admin && isActiveLecturerActor(actor);
        String actorLecturerId = lecturer ? lecturerId(actor) : "";
        boolean member = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId(actor))) > 0;
        boolean supervisor = lecturer
                && count("SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", actorLecturerId)) > 0;
        boolean councilMember = lecturer
                && group.topicId() != null
                && count("SELECT COUNT(*) FROM thesis.thesis_council_member cm "
                        + "JOIN thesis.thesis_council_topic ct ON ct.council_id = cm.council_id "
                        + "WHERE ct.topic_id = :topicId AND cm.lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", actorLecturerId)) > 0;
        // The assigned counter-reviewer must read the report to grade it —
        // the assignment may cross departments, so the archive scope cannot
        // cover this role.
        boolean gvpbReviewer = lecturer
                && group.topicId() != null
                && count("SELECT COUNT(*) FROM thesis.thesis_topic WHERE id = :topicId AND gvpb_lecturer_id = :lecturerId",
                        params().addValue("topicId", group.topicId()).addValue("lecturerId", actorLecturerId)) > 0;
        boolean sameDepartmentArchive = lecturer
                && "APPROVED".equals(group.approvalStatus())
                && !"CANCELLED".equals(group.status())
                && sameDepartment(group.topicDepartmentId(), actor);
        boolean authorized = member || supervisor || councilMember || admin || facultyReader
                || gvpbReviewer || sameDepartmentArchive;
        if (!authorized && (lecturer || dean)) {
            // Do not disclose whether a report exists to a lecturer outside the
            // department archive scope.
            throw notFound("REPORT_NOT_FOUND", "The report is not available in your department archive");
        }
        if (!authorized) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED",
                    "Only group members, supervisors, council reviewers, or staff can read the report");
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
        MapSqlParameterSource parameters = params().addValue("roundId", roundId);
        // Staff archive (plan F6): an admin sees every report row of the round
        // — a group reopened to PENDING or one without a topic must not hide
        // its submitted deliverable. The lecturer branch keeps the approval
        // and department scope; a topic-less row self-excludes there because
        // its NULL department_id never matches the lecturer's.
        String filter = archiveFilter(actor, parameters);
        return jdbc.query(
                "SELECT r.group_id, r.title, r.url, r.note, r.submitted_by, r.submitted_at, r.updated_at, "
                        + "r.file_name, r.file_type, r.file_size "
                        + "FROM thesis.thesis_group_report r "
                        + "JOIN thesis.thesis_group g ON g.id = r.group_id "
                        + "LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "WHERE r.round_id = :roundId" + filter + " ORDER BY r.submitted_at DESC, r.group_id",
                parameters,
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

    /**
     * Server-owned archive projection. The query is already authorization
     * scoped; callers must not rebuild this response from lecturer-scoped
     * groups or topic endpoints.
     */
    @Transactional(readOnly = true)
    public List<RepositoryReportResponse> listRepository(UUID roundId, Jwt actor) {
        MapSqlParameterSource parameters = params().addValue("roundId", roundId);
        // Same archive rule as listByRound: admins see every report of the
        // round; lecturers keep the approval + department scope (topic-less
        // rows self-exclude via the NULL department join).
        String filter = archiveFilter(actor, parameters);
        String sql = """
                SELECT r.id AS report_id, r.title, r.url, r.note, r.submitted_at, r.updated_at,
                       r.file_name, r.file_type, r.file_size,
                       t.title AS topic_title, t.description AS topic_description,
                       COALESCE(d."name", t.department_id) AS department_name,
                       g.status AS group_status, g.approval_status,
                       gm.student_id, gm.display_name AS external_display_name,
                       gm.is_external, gm.is_leader, gm.member_order,
                       s."studentId" AS student_number,
                       member_user."firstName" AS member_first_name,
                       member_user."lastName" AS member_last_name,
                       sup.lecturer_id, sup.supervisor_order,
                       supervisor_user."firstName" AS supervisor_first_name,
                       supervisor_user."lastName" AS supervisor_last_name,
                       submitted_student."studentId" AS submitter_student_number,
                       submitter_user."firstName" AS submitter_first_name,
                       submitter_user."lastName" AS submitter_last_name
                FROM thesis.thesis_group_report r
                 JOIN thesis.thesis_group g ON g.id = r.group_id
                 LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id
                 LEFT JOIN academic."Department" d ON d."id" = t.department_id
                LEFT JOIN thesis.thesis_group_member gm ON gm.group_id = g.id
                LEFT JOIN campuscore_auth."Student" s ON s."id" = gm.student_id
                LEFT JOIN campuscore_auth."User" member_user ON member_user."id" = s."userId"
                LEFT JOIN thesis.thesis_topic_supervisor sup ON sup.topic_id = t.id
                LEFT JOIN campuscore_auth."Lecturer" supervisor_profile
                    ON supervisor_profile."id" = sup.lecturer_id
                LEFT JOIN campuscore_auth."User" supervisor_user
                    ON supervisor_user."id" = supervisor_profile."userId"
                LEFT JOIN campuscore_auth."Student" submitted_student
                    ON submitted_student."id" = r.submitted_by
                LEFT JOIN campuscore_auth."User" submitter_user
                    ON submitter_user."id" = submitted_student."userId"
                WHERE r.round_id = :roundId
                """ + filter + " ORDER BY r.submitted_at DESC, gm.member_order, sup.supervisor_order";
        Map<UUID, RepositoryBuilder> builders = new LinkedHashMap<>();
        jdbc.query(sql, parameters, rs -> {
            UUID reportId = UUID.fromString(rs.getString("report_id"));
            RepositoryBuilder builder = builders.get(reportId);
            if (builder == null) {
                builder = new RepositoryBuilder(
                        reportId,
                        rs.getString("title"),
                        rs.getString("url"),
                        rs.getString("note"),
                        instantOf(rs.getObject("submitted_at")),
                        instantOf(rs.getObject("updated_at")),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        (Long) rs.getObject("file_size"),
                        rs.getString("topic_title"),
                        rs.getString("topic_description"),
                        rs.getString("department_name"),
                        rs.getString("group_status"),
                        rs.getString("approval_status"),
                        displayName(rs.getString("submitter_first_name"), rs.getString("submitter_last_name")),
                        rs.getString("submitter_student_number"));
                builders.put(reportId, builder);
            }
            String memberId = rs.getString("student_id");
            if (memberId != null) {
                builder.members.putIfAbsent(memberKey(memberId, rs.getInt("member_order")),
                        new RepositoryMemberResponse(
                                displayName(rs.getString("member_first_name"), rs.getString("member_last_name"),
                                        rs.getString("external_display_name"), rs.getString("student_number")),
                                rs.getString("student_number"),
                                rs.getBoolean("is_external"),
                                rs.getBoolean("is_leader")));
            }
            String supervisorId = rs.getString("lecturer_id");
            if (supervisorId != null) {
                builder.supervisors.putIfAbsent(supervisorId,
                        new RepositorySupervisorResponse(
                                displayName(rs.getString("supervisor_first_name"), rs.getString("supervisor_last_name")),
                                rs.getInt("supervisor_order")));
            }
        });
        return builders.values().stream().map(RepositoryBuilder::build).toList();
    }

    /** Download by opaque report id for the repository projection. */
    @Transactional(readOnly = true)
    public StoredReport downloadByReport(UUID reportId, Jwt actor) {
        UUID groupId = oneUuid("SELECT group_id FROM thesis.thesis_group_report WHERE id = :reportId",
                params().addValue("reportId", reportId), "REPORT_NOT_FOUND", "The report was not found");
        get(groupId, actor);
        try {
            return jdbc.queryForObject(
                    "SELECT file_name, file_type, file_data, storage_provider, storage_bucket, storage_key, file_sha256 "
                            + "FROM thesis.thesis_group_report WHERE id = :reportId",
                    params().addValue("reportId", reportId),
                    (rs, ignored) -> storedReportFromRow(rs));
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("REPORT_FILE_NOT_FOUND", "The current report is a link, not an attached document");
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse getByTopic(UUID topicId, Jwt actor) {
        // Deterministic pick: max_groups lets several approved groups share a
        // topic, and an unordered LIMIT-1 made the returned report arbitrary.
        // A member of a later-approved group must land on their own group's
        // report, so caller membership outranks the oldest-approved fallback.
        java.util.List<UUID> groupIds = jdbc.query(
                "SELECT g.id FROM thesis.thesis_group g WHERE g.topic_id = :topicId AND g.approval_status = 'APPROVED' "
                        + "ORDER BY CASE WHEN EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_group_member m "
                        + "  WHERE m.group_id = g.id AND m.student_id = :studentId"
                        + ") THEN 0 ELSE 1 END, g.created_at, g.id LIMIT 1",
                params().addValue("topicId", topicId).addValue("studentId", studentId(actor)),
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

    /** Match roster mutations' group-before-round order; publication owns the round fence. */
    private GroupContext lockGroupForWrite(UUID groupId) {
        Map<String, Object> group = one(
                "SELECT round_id FROM thesis.thesis_group WHERE id = :groupId FOR UPDATE",
                params().addValue("groupId", groupId), "GROUP_NOT_FOUND", "Thesis group not found");
        one("SELECT id FROM thesis.thesis_registration_round WHERE id = :roundId FOR UPDATE",
                params().addValue("roundId", group.get("round_id")), "ROUND_NOT_FOUND", "Thesis round not found");
        // Re-read eligibility only after both locks: an in-flight publication,
        // roster change or deadline extension must not leave stale authority.
        return loadGroup(groupId);
    }

    private GroupContext loadGroup(UUID groupId) {
        Map<String, Object> group = one(
                "SELECT g.id, g.round_id, g.leader_student_id, g.topic_id, g.status, g.approval_status, "
                        + "t.department_id AS topic_department_id, "
                        + "COALESCE(r.report_deadline, r.gvpb_deadline) AS report_deadline, "
                        + "r.status AS round_status "
                        + "FROM thesis.thesis_group g JOIN thesis.thesis_registration_round r ON r.id = g.round_id "
                        + "LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "WHERE g.id = :groupId",
                params().addValue("groupId", groupId), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupContext(
                (UUID) group.get("id"),
                (UUID) group.get("round_id"),
                (String) group.get("leader_student_id"),
                (UUID) group.get("topic_id"),
                group.get("report_deadline") == null ? null : instantOf(group.get("report_deadline")),
                roundStatusOf(group.get("round_status")),
                (String) group.get("status"),
                (String) group.get("approval_status"),
                (String) group.get("topic_department_id"));
    }

    private String requireDeanFaculty(Jwt actor) {
        ThesisGroupReadRepository.LecturerScope scope = groups.findLecturerScope(lecturerId(actor), actor.getSubject());
        if (scope == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        return scope.facultyId();
    }

    /** Staff history stays readable regardless of approval; only its ownership scope changes. */
    private String archiveFilter(Jwt actor, MapSqlParameterSource parameters) {
        if (hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN")) return "";
        if (hasRole(actor, "TRUONG_KHOA")) {
            parameters.addValue("facultyId", requireDeanFaculty(actor));
            String filter = """
                    AND (EXISTS (
                        SELECT 1 FROM academic."Department" fd
                        WHERE fd."id" = t.department_id AND fd."facultyId" = :facultyId)
                    OR (g.topic_id IS NULL AND EXISTS (
                        SELECT 1 FROM thesis.thesis_group_member fm
                        JOIN campuscore_auth."Student" fs ON fs."id" = fm.student_id
                        JOIN academic."Curriculum" fc ON fc."id" = fs."curriculumId"
                        JOIN academic."Department" fd ON fd."id" = fc."departmentId"
                        WHERE fm.group_id = g.id AND fd."facultyId" = :facultyId))
                    """;
            // The faculty lookup already verified this lecturer id belongs to the active caller.
            if (hasRole(actor, "LECTURER")) {
                parameters.addValue("assignedLecturerId", lecturerId(actor));
                filter += """
                        OR t.gvpb_lecturer_id = :assignedLecturerId
                        OR EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor assigned
                            WHERE assigned.topic_id = t.id AND assigned.lecturer_id = :assignedLecturerId)
                        OR EXISTS (SELECT 1 FROM thesis.thesis_council_member cm
                            JOIN thesis.thesis_council_topic ct ON ct.council_id = cm.council_id
                            WHERE ct.topic_id = t.id AND cm.lecturer_id = :assignedLecturerId)
                        """;
            }
            return " " + filter + ")";
        }
        if (!isActiveLecturerActor(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_OR_STAFF_REQUIRED",
                    "Only lecturers or academic staff can access the round thesis archive");
        }
        parameters.addValue("departmentId", activeLecturerDepartment(actor));
        return " AND g.approval_status = 'APPROVED' AND g.status <> 'CANCELLED'"
                + " AND t.department_id = :departmentId";
    }

    private boolean sameDepartment(String topicDepartmentId, Jwt actor) {
        if (!StringUtils.hasText(topicDepartmentId)) return false;
        return topicDepartmentId.equals(activeLecturerDepartment(actor));
    }

    private String activeLecturerDepartment(Jwt actor) {
        String id = lecturerId(actor);
        if (!StringUtils.hasText(id)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required for the thesis archive");
        }
        try {
            String department = jdbc.queryForObject(
                    "SELECT \"departmentId\" FROM campuscore_auth.\"Lecturer\" "
                            + "WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                    params().addValue("lecturerId", id), String.class);
            if (!StringUtils.hasText(department)) throw new EmptyResultDataAccessException(1);
            return department;
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required for the thesis archive");
        }
    }

    /**
     * A lecturer identifier is an identity attribute, not an authority by
     * itself.  Only a token carrying the lecturer role and an active database
     * profile may enter lecturer-scoped archive/supervisor/council branches.
     * Faculty heads also require their separate subject-bound faculty lookup.
     */
    private boolean isActiveLecturerActor(Jwt actor) {
        if (!hasRole(actor, "LECTURER") && !hasRole(actor, "TRUONG_BO_MON")) {
            return false;
        }
        String id = lecturerId(actor);
        if (!StringUtils.hasText(id)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required for the thesis archive");
        }
        if (count("SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" "
                        + "WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                params().addValue("lecturerId", id)) == 0) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required for the thesis archive");
        }
        return true;
    }

    private record GroupContext(UUID id, UUID roundId, String leaderStudentId, UUID topicId, Instant reportDeadline,
                                RoundStatus roundStatus, String status, String approvalStatus,
                                String topicDepartmentId) { }

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

    public record RepositoryReportResponse(
            UUID reportId,
            String title,
            String url,
            String note,
            Instant submittedAt,
            Instant updatedAt,
            String fileName,
            String fileType,
            Long fileSize,
            String topicTitle,
            String topicDescription,
            String departmentName,
            String groupStatus,
            String approvalStatus,
            String submittedByDisplayName,
            String submittedByStudentNumber,
            List<RepositorySupervisorResponse> supervisors,
            List<RepositoryMemberResponse> members) { }

    public record RepositorySupervisorResponse(String displayName, int supervisorOrder) { }

    public record RepositoryMemberResponse(
            String displayName,
            String studentNumber,
            boolean isExternal,
            boolean isLeader) { }

    private static final class RepositoryBuilder {
        private final UUID reportId;
        private final String title;
        private final String url;
        private final String note;
        private final Instant submittedAt;
        private final Instant updatedAt;
        private final String fileName;
        private final String fileType;
        private final Long fileSize;
        private final String topicTitle;
        private final String topicDescription;
        private final String departmentName;
        private final String groupStatus;
        private final String approvalStatus;
        private final String submittedByDisplayName;
        private final String submittedByStudentNumber;
        private final Map<String, RepositoryMemberResponse> members = new LinkedHashMap<>();
        private final Map<String, RepositorySupervisorResponse> supervisors = new LinkedHashMap<>();

        private RepositoryBuilder(UUID reportId, String title, String url, String note, Instant submittedAt,
                                  Instant updatedAt, String fileName, String fileType, Long fileSize,
                                  String topicTitle, String topicDescription, String departmentName,
                                  String groupStatus, String approvalStatus, String submittedByDisplayName,
                                  String submittedByStudentNumber) {
            this.reportId = reportId;
            this.title = title;
            this.url = url;
            this.note = note;
            this.submittedAt = submittedAt;
            this.updatedAt = updatedAt;
            this.fileName = fileName;
            this.fileType = fileType;
            this.fileSize = fileSize;
            this.topicTitle = topicTitle;
            this.topicDescription = topicDescription;
            this.departmentName = departmentName;
            this.groupStatus = groupStatus;
            this.approvalStatus = approvalStatus;
            this.submittedByDisplayName = submittedByDisplayName;
            this.submittedByStudentNumber = submittedByStudentNumber;
        }

        private RepositoryReportResponse build() {
            return new RepositoryReportResponse(reportId, title, url, note, submittedAt, updatedAt, fileName,
                    fileType, fileSize, topicTitle, topicDescription, departmentName, groupStatus,
                    approvalStatus, submittedByDisplayName, submittedByStudentNumber,
                    List.copyOf(supervisors.values()), List.copyOf(members.values()));
        }
    }

    /** The attached document bytes, served only through the authorized download path. */
    public record StoredReport(String fileName, String contentType, byte[] data) { }

    private void requireWriteAccess(GroupContext group, Jwt actor) {
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED",
                    "Only the group leader can submit the topic report");
        }
    }

    /**
     * The report deliverable is writable only inside the round's report window,
     * and that window is resolved per round type so it is reachable for every
     * kind of round:
     *
     * <ul>
     *   <li>TLCN/KLTN author a GVPB grading deadline ({@link
     *       io.campuscore.restfulapi.thesis.domain.RoundType#requiresGvpbDeadline()}).
     *       Reports freeze the moment that date passes, because the grading
     *       committee reads the artifact from then on.</li>
     *   <li>MON_HOC/NCKH deliberately author no grading, council or defense
     *       date, so their freeze source is the round's own lifecycle: the
     *       report is writable once registration has closed and frozen as soon
     *       as the round leaves REGISTRATION_CLOSED, i.e. when the results are
     *       published. The last date those rounds carry is registration_end,
     *       which is the date the deliverable window <em>opens</em>; keying the
     *       freeze to it would make their reports unwritable, which is worse
     *       than leaving them unfrozen.</li>
     *   <li>A round that carries no grading deadline at all degrades safely: a
     *       missing date never freezes a writable report, and the lifecycle
     *       window still bounds it.</li>
     * </ul>
     *
     * <p>Sequencing for every round type: a report is written after the group
     * is approved (see {@link #requireApproved(UUID)}) and after registration
     * closes, and never after the round's results are official.
     */
    private void requireReportWritable(GroupContext group) {
        Instant reportDeadline = group.reportDeadline();
        if (reportDeadline != null && !Instant.now().isBefore(reportDeadline)) {
            throw conflict("REPORT_DEADLINE_PASSED",
                    "The report submission deadline for this round has passed; reports are frozen");
        }
        if (group.roundStatus() != RoundStatus.REGISTRATION_CLOSED) {
            throw conflict("REPORT_WINDOW_CLOSED",
                    "Reports can be submitted or replaced only after the round's registration closes and before "
                            + "its results are published (this round is " + group.roundStatus() + ")");
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
                data = readOrphanedLocalObject(key, storedProvider);
            } else {
                try {
                    data = storage.read(key);
                } catch (ThesisReportStorageException exception) {
                    throw storageUnavailable();
                }
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

    /**
     * Provider-switch tolerance for rows written while the {@code local}
     * provider was active (e.g. before a move to {@code database} on an
     * ephemeral host). The local root is a fixed configuration property, so
     * the row's object stays readable while the file still exists under it —
     * a bucket label difference does not move the file. Every other provider
     * mismatch stays a loud {@code REPORT_STORAGE_UNAVAILABLE}: a remote
     * object's bytes cannot be re-derived without its original credentials.
     */
    private byte[] readOrphanedLocalObject(String key, String storedProvider) {
        if (!"local".equals(storedProvider)) {
            throw storageUnavailable();
        }
        try {
            return new LocalThesisReportStorage(storageProperties).read(key);
        } catch (RuntimeException exception) {
            throw storageUnavailable();
        }
    }

    private void cleanupStoredObject(StoredObjectRef previous) {
        if (previous == null || !storage.provider().equals(previous.provider())
                || !storage.bucket().equals(previous.bucket())) {
            return;
        }
        // Defer the old object's deletion until the transaction commits: a
        // rollback after this point would otherwise leave the surviving row
        // pointing at a deleted object — permanent data loss once storage is
        // durable (Supabase), self-healing only while it is ephemeral.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupNow(previous);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupNow(previous);
            }
        });
    }

    private void cleanupNow(StoredObjectRef previous) {
        try {
            storage.delete(previous.key());
        } catch (ThesisReportStorageException exception) {
            // The DB row is already authoritative. Leave cleanup for an
            // operator job instead of making a successful submission look like
            // a failure because an old object was already missing.
            log.warn("Unable to remove replaced thesis report object");
        }
    }

    private boolean registerNewObjectRollbackCleanup(String key) {
        if (!StringUtils.hasText(key) || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteNewObjectAfterFailure(key);
                } else if (status == STATUS_UNKNOWN) {
                    // An uncertain commit may already reference this object;
                    // retaining it is safer than deleting committed bytes.
                    log.warn("Thesis report upload transaction outcome is unknown; object retained");
                }
            }
        });
        return true;
    }

    private void deleteNewObjectAfterFailure(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
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

    /** {@code thesis_registration_round.status} is a constrained column; unknown values stay unreadable rather than open. */
    private static RoundStatus roundStatusOf(Object value) {
        if (value == null) return null;
        try {
            return RoundStatus.valueOf(value.toString().trim());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
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

    private UUID oneUuid(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            Object value = jdbc.queryForObject(sql, parameters, Object.class);
            if (value == null) throw new EmptyResultDataAccessException(1);
            return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private static String memberKey(String studentId, int order) {
        return studentId + ":" + order;
    }

    private static String displayName(String firstName, String lastName) {
        return displayName(firstName, lastName, null, null);
    }

    private static String displayName(String firstName, String lastName, String fallback, String secondFallback) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String name = (last + " " + first).trim();
        if (!name.isBlank()) return name;
        if (fallback != null && !fallback.isBlank()) return fallback.trim();
        if (secondFallback != null && !secondFallback.isBlank()) return secondFallback.trim();
        return null;
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
