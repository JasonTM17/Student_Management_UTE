package io.campuscore.restfulapi.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentPage;
import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentRequest;
import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentView;
import io.campuscore.restfulapi.registration.RegistrationDtos.EligibilityView;
import io.campuscore.restfulapi.registration.RegistrationDtos.RoundPage;
import io.campuscore.restfulapi.registration.RegistrationDtos.RoundView;
import io.campuscore.restfulapi.registration.RegistrationDtos.SectionView;
import io.campuscore.restfulapi.registration.RegistrationDtos.ScheduleView;
import io.campuscore.restfulapi.registration.RegistrationDtos.SummaryView;
import io.campuscore.restfulapi.registration.RegistrationDtos.ValidationResponse;
import io.campuscore.restfulapi.academic.persistence.AcademicSectionEntity;
import io.campuscore.restfulapi.academic.persistence.AcademicSectionRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentEntity;
import io.campuscore.restfulapi.academic.persistence.EnrollmentRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentOperationEntity;
import io.campuscore.restfulapi.academic.persistence.EnrollmentOperationRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentAuditEntity;
import io.campuscore.restfulapi.academic.persistence.EnrollmentAuditRepository;
import io.campuscore.restfulapi.academic.persistence.RegistrationRoundEntity;
import io.campuscore.restfulapi.academic.persistence.RegistrationRoundRepository;
import io.campuscore.restfulapi.academic.persistence.RegistrationSlipEntity;
import io.campuscore.restfulapi.academic.persistence.RegistrationSlipRepository;
import io.campuscore.restfulapi.academic.persistence.RegistrationJpaMutationGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Registration orchestration. Locks are acquired operation, round, section, then student. */
@Service
@Profile("persistence")
public class RegistrationService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String ACTIVE = "lower(e.\"status\") in ('active','enrolled','pending','confirmed')";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final RegistrationRoundRepository roundRepository;
    private final AcademicSectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentOperationRepository operationRepository;
    private final EnrollmentAuditRepository auditRepository;
    private final RegistrationSlipRepository slipRepository;
    private final RegistrationJpaMutationGateway mutationGateway;

    public RegistrationService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, Clock clock,
            RegistrationRoundRepository roundRepository, AcademicSectionRepository sectionRepository,
            EnrollmentRepository enrollmentRepository, EnrollmentOperationRepository operationRepository,
            EnrollmentAuditRepository auditRepository, RegistrationSlipRepository slipRepository,
            RegistrationJpaMutationGateway mutationGateway) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.roundRepository = roundRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.operationRepository = operationRepository;
        this.auditRepository = auditRepository;
        this.slipRepository = slipRepository;
        this.mutationGateway = mutationGateway;
    }

    public RoundPage rounds(String semesterId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit <= 0 ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        int offset = decodeCursor(cursor);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("limit", safeLimit + 1).addValue("offset", offset);
        String sql = "SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\","
                + "r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\",r.\"version\" "
                + "FROM academic.\"RegistrationRound\" r" + (semesterId == null ? "" : " WHERE r.\"semesterId\"=:semesterId")
                + " ORDER BY r.\"registrationStart\" DESC,r.\"id\" LIMIT :limit OFFSET :offset";
        if (semesterId != null) p.addValue("semesterId", semesterId.trim());
        List<RoundView> rows = jdbc.query(sql, p, roundMapper());
        String next = rows.size() > safeLimit ? encodeCursor(offset + safeLimit) : null;
        return new RoundPage(rows.subList(0, Math.min(rows.size(), safeLimit)), next);
    }

    public RoundView round(String id) {
        return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\","
                + "r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\",r.\"version\" FROM academic.\"RegistrationRound\" r WHERE r.\"id\"=:id",
                new MapSqlParameterSource("id", required(id, "roundId")), roundMapper()).stream().findFirst()
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "REGISTRATION_ROUND_NOT_FOUND", "Registration round not found"));
    }

    public List<SectionView> sections(String roundId, String studentId) {
        RoundView round = round(roundId);
        String sql = "SELECT s.\"id\",s.\"courseId\",c.\"code\",c.\"name\",c.\"credits\",s.\"sectionNumber\","
                + "s.\"capacity\",s.\"enrolledCount\",s.\"status\","
                + "coalesce(trim(u.\"firstName\"||' '||u.\"lastName\"),'') lecturer_name,"
                + "coalesce(cl.\"building\"||' '||cl.\"roomNumber\",'') classroom "
                + "FROM academic.\"Section\" s JOIN academic.\"Course\" c ON c.\"id\"=s.\"courseId\" "
                + "LEFT JOIN academic.\"Lecturer\" l ON l.\"id\"=s.\"lecturerId\" LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\"=l.\"userId\" "
                + "LEFT JOIN academic.\"Classroom\" cl ON cl.\"id\"=s.\"classroomId\" WHERE s.\"semesterId\"=:semesterId ORDER BY c.\"code\",s.\"sectionNumber\",s.\"id\"";
        MapSqlParameterSource p = new MapSqlParameterSource("semesterId", round.semesterId());
        Set<String> selected = studentId == null ? Set.of() : new HashSet<>(jdbc.queryForList(
                "SELECT e.\"sectionId\" FROM academic.\"Enrollment\" e WHERE e.\"studentId\"=:studentId AND " + ACTIVE,
                new MapSqlParameterSource("studentId", studentId), String.class));
        return jdbc.query(sql, p, (rs, n) -> section(rs, selected.contains(rs.getString("id"))));
    }

    public EligibilityView eligibility(String studentId, String roundId, Integer studentYear) {
        RoundView round = round(roundId);
        int selected = selectedCredits(studentId, round.semesterId());
        Integer priority = null;
        String reason = null;
        if (studentId == null || studentId.isBlank()) {
            reason = "STUDENT_PROFILE_REQUIRED";
        } else if (studentYear != null) {
            String cohort = String.valueOf(studentYear);
            List<Integer> ranks = jdbc.queryForList("SELECT w.\"priorityRank\" FROM academic.\"RegistrationCohortWindow\" w WHERE w.\"roundId\"=:roundId AND w.\"cohortCode\"=:cohort",
                    new MapSqlParameterSource().addValue("roundId", roundId).addValue("cohort", cohort), Integer.class);
            if (!ranks.isEmpty()) {
                priority = ranks.get(0);
                Instant now = clock.instant();
                Long inWindow = jdbc.queryForObject("SELECT count(*) FROM academic.\"RegistrationCohortWindow\" w WHERE w.\"roundId\"=:roundId AND w.\"cohortCode\"=:cohort AND :now BETWEEN w.\"windowStart\" AND w.\"windowEnd\"",
                        new MapSqlParameterSource().addValue("roundId", roundId).addValue("cohort", cohort).addValue("now", Timestamp.from(now)), Long.class);
                if (inWindow != null && inWindow == 0) reason = "COHORT_NOT_ELIGIBLE";
            }
        }
        return new EligibilityView(roundId, reason == null ? "ELIGIBLE" : "INELIGIBLE", priority,
                round.maxCredits(), selected, reason, clock.instant());
    }

    public SummaryView summary(String studentId, String roundId) {
        RoundView round = round(roundId);
        List<SectionView> sections = sections(roundId, studentId).stream().filter(SectionView::selected).toList();
        return new SummaryView(roundId, sections.stream().mapToInt(SectionView::credits).sum(), round.maxCredits(), sections.size(), sections);
    }

    public EnrollmentPage enrollments(String studentId, String semesterId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit <= 0 ? 50 : limit, 1), MAX_LIMIT);
        int offset = decodeCursor(cursor);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT e.\"id\",e.\"sectionId\",e.\"semesterId\",e.\"roundId\",e.\"status\",e.\"enrolledAt\" FROM academic.\"Enrollment\" e WHERE e.\"studentId\"=:studentId"
                + (semesterId == null ? "" : " AND e.\"semesterId\"=:semesterId") + " ORDER BY e.\"enrolledAt\" DESC,e.\"id\" LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("limit", safeLimit + 1).addValue("offset", offset)
                        .addValue("semesterId", semesterId));
        List<EnrollmentView> result = rows.stream().limit(safeLimit).map(row -> enrollment(row, studentId)).toList();
        return new EnrollmentPage(result, rows.size() > safeLimit ? encodeCursor(offset + safeLimit) : null);
    }

    public ValidationResponse validate(String studentId, EnrollmentRequest request) {
        List<String> violations = validateViolations(studentId, request);
        return new ValidationResponse(violations.isEmpty(), violations);
    }

    @Transactional
    public MutationResult enroll(String studentId, EnrollmentRequest request, UUID key) {
        requireStudent(studentId);
        String hash = canonicalHash("ENROLL", studentId, request);
        OperationReservation reservation = reserveOperation(studentId, key, hash, "ENROLL");
        if (reservation.replay() != null) {
            return new MutationResult(replay(reservation.replay(), studentId), true);
        }
        MutationLocks locks = lockMutation(request.roundId(), request.sectionId(), studentId);
        RoundView round = locks.round();
        AcademicSectionEntity lockedSection = locks.section();
        EnrollmentOperationEntity operation = reservation.operation();
        List<String> violations = validateViolations(studentId, request);
        if (!violations.isEmpty()) throw rejection(violations.get(0), violations);
        Instant now = clock.instant();
        String enrollmentId = UUID.randomUUID().toString();
        enrollmentRepository.save(EnrollmentEntity.enrolled(enrollmentId, studentId, request.sectionId(), round.semesterId(), round.id(), now));
        try {
            lockedSection.incrementEnrollment();
        } catch (IllegalStateException capacityFailure) {
            throw capacityProblem(capacityFailure.getMessage());
        }
        sectionRepository.saveAndFlush(lockedSection);
        EnrollmentView persisted = enrollment(Map.of("id", enrollmentId, "sectionId", request.sectionId(), "semesterId", round.semesterId(), "roundId", round.id(), "status", "ENROLLED", "enrolledAt", now), studentId);
        EnrollmentView response = new EnrollmentView(persisted.id(), persisted.sectionId(), round.id(), persisted.status(), persisted.enrolledAt(), persisted.section());
        completeOperation(operation, response, 201);
        auditRepository.save(EnrollmentAuditEntity.of(UUID.randomUUID().toString(), operation.getId(), studentId, request.sectionId(), "ENROLL", "ENROLLED", now));
        return new MutationResult(response, false);
    }

    @Transactional
    public MutationResult enrollBySection(String studentId, String sectionId, UUID key) {
        RoundView round = roundForSection(required(sectionId, "sectionId"));
        return enroll(studentId, new EnrollmentRequest(sectionId, round.id()), key);
    }

    @Transactional
    public DropResult drop(String studentId, String enrollmentId, UUID key) {
        requireStudent(studentId);
        EnrollmentEntity previewEnrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (!studentId.equals(previewEnrollment.getStudentId())) throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_NOT_OWNER", "Enrollment does not belong to the current student");
        String hash = sha256("DROP|" + studentId + "|" + enrollmentId);
        RoundView candidateRound = round(previewEnrollment.getRoundId());
        OperationReservation reservation = reserveOperation(studentId, key, hash, "DROP");
        if (reservation.replay() != null) return new DropResult(enrollmentId, true);
        MutationLocks locks = lockMutation(candidateRound.id(), previewEnrollment.getSectionId(), studentId);
        RoundView round = locks.round();
        AcademicSectionEntity lockedSection = locks.section();
        Instant now = clock.instant();
        if (!"OPEN".equalsIgnoreCase(round.status()) || now.isAfter(round.addDropEnd()) || now.isBefore(round.addDropStart())) {
            throw problem(HttpStatus.CONFLICT, "ADD_DROP_CLOSED", "Add/drop window is closed");
        }
        EnrollmentOperationEntity operation = reservation.operation();
        EnrollmentEntity lockedEnrollment = enrollmentRepository.findLockedById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (!studentId.equals(lockedEnrollment.getStudentId())) {
            throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_NOT_OWNER", "Enrollment does not belong to the current student");
        }
        String currentStatus = lockedEnrollment.getStatus() == null ? "" : lockedEnrollment.getStatus().toUpperCase(java.util.Locale.ROOT);
        if ("COMPLETED".equals(currentStatus)) {
            throw problem(HttpStatus.CONFLICT, "ALREADY_COMPLETED", "Completed enrollments cannot be dropped");
        }
        if (!isActiveEnrollmentStatus(currentStatus)) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment is not active");
        }
        lockedEnrollment.markDropped(now);
        enrollmentRepository.save(lockedEnrollment);
        try {
            lockedSection.decrementEnrollment();
        } catch (IllegalStateException countFailure) {
            throw capacityProblem(countFailure.getMessage());
        }
        sectionRepository.saveAndFlush(lockedSection);
        completeOperation(operation, Map.of("dropped", true, "enrollmentId", enrollmentId), 200);
        auditRepository.save(EnrollmentAuditEntity.of(UUID.randomUUID().toString(), operation.getId(), studentId, lockedEnrollment.getSectionId(), "DROP", "DROPPED", now));
        return new DropResult(enrollmentId, false);
    }

    @Transactional
    public DropResult dropAsAdmin(String enrollmentId, UUID key) {
        EnrollmentEntity previewEnrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        String studentId = previewEnrollment.getStudentId();
        String hash = sha256("DROP_ADMIN|" + studentId + "|" + enrollmentId);
        RoundView candidateRound = round(previewEnrollment.getRoundId());
        OperationReservation reservation = reserveOperation(studentId, key, hash, "DROP");
        if (reservation.replay() != null) return new DropResult(enrollmentId, true);
        MutationLocks locks = lockMutation(candidateRound.id(), previewEnrollment.getSectionId(), studentId);
        AcademicSectionEntity lockedSection = locks.section();
        EnrollmentEntity lockedEnrollment = enrollmentRepository.findLockedById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        String currentStatus = lockedEnrollment.getStatus() == null ? ""
                : lockedEnrollment.getStatus().toUpperCase(java.util.Locale.ROOT);
        if ("COMPLETED".equals(currentStatus)) {
            throw problem(HttpStatus.CONFLICT, "ALREADY_COMPLETED", "Completed enrollments cannot be dropped");
        }
        if (!isActiveEnrollmentStatus(currentStatus)) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment is not active");
        }
        Instant now = clock.instant();
        lockedEnrollment.markDropped(now);
        enrollmentRepository.save(lockedEnrollment);
        try {
            lockedSection.decrementEnrollment();
        } catch (IllegalStateException countFailure) {
            throw capacityProblem(countFailure.getMessage());
        }
        sectionRepository.saveAndFlush(lockedSection);
        EnrollmentOperationEntity operation = reservation.operation();
        completeOperation(operation, Map.of("dropped", true, "enrollmentId", enrollmentId), 200);
        auditRepository.save(EnrollmentAuditEntity.of(UUID.randomUUID().toString(), operation.getId(), studentId,
                lockedEnrollment.getSectionId(), "DROP", "DROPPED_BY_ADMIN", now));
        return new DropResult(enrollmentId, false);
    }

    @Transactional
    public SlipResult slip(String studentId, String roundId) {
        RoundView round = round(roundId);
        var stored = slipRepository.findLockedByStudentIdAndRoundId(studentId, roundId);
        if (stored.isPresent() && stored.get().getSnapshotPayload() != null && !stored.get().getSnapshotPayload().isBlank()) {
            return storedSlip(stored.get());
        }
        Instant generatedAt = clock.instant();
        List<Map<String, Object>> roundRows = jdbc.queryForList(
                "SELECT e.\"id\",e.\"sectionId\",e.\"semesterId\",e.\"roundId\",e.\"status\",e.\"enrolledAt\" "
                        + "FROM academic.\"Enrollment\" e WHERE e.\"studentId\"=:studentId AND e.\"roundId\"=:roundId "
                        + "ORDER BY e.\"enrolledAt\" DESC,e.\"id\" LIMIT :limit",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("roundId", round.id()).addValue("limit", MAX_LIMIT));
        List<EnrollmentView> enrollments = roundRows.stream().map(row -> enrollment(row, studentId))
                .filter(enrollment -> isActiveEnrollmentStatus(enrollment.status()))
                .toList();
        String canonical = canonicalSlip(studentId, round, enrollments, generatedAt);
        byte[] pdf = SimplePdf.render(canonical);
        String checksum = sha256(pdf);
        String encodedPdf = Base64.getEncoder().encodeToString(pdf);
        if (stored.isEmpty()) {
            // The natural key can be longer than the schema's bounded id column
            // (both student and round identifiers are user/seed controlled).
            // Keep idempotency on (studentId, roundId), but use a deterministic
            // fixed-width surrogate for the physical primary key.
            mutationGateway.insertSlipIfAbsent(slipId(studentId, roundId), studentId, roundId,
                    checksum, encodedPdf, generatedAt);
            RegistrationSlipEntity winner = slipRepository.findLockedByStudentIdAndRoundId(studentId, roundId)
                    .orElseThrow(() -> new IllegalStateException("Registration slip insert was not visible"));
            return storedSlip(winner);
        } else {
            stored.get().storePayload(encodedPdf, checksum);
            slipRepository.saveAndFlush(stored.get());
        }
        return new SlipResult(pdf, checksum);
    }

    private SlipResult storedSlip(RegistrationSlipEntity stored) {
        try {
            byte[] pdf = Base64.getDecoder().decode(stored.getSnapshotPayload());
            String checksum = sha256(pdf);
            stored.storePayload(stored.getSnapshotPayload(), checksum);
            slipRepository.save(stored);
            return new SlipResult(pdf, checksum);
        } catch (IllegalArgumentException | IllegalStateException malformedSnapshot) {
            throw problem(HttpStatus.CONFLICT, "REGISTRATION_SLIP_SNAPSHOT_INVALID", "Stored registration slip snapshot is invalid");
        }
    }

    /** Canonical, human-readable slip body. Ordering and field labels are part of the hash contract. */
    static String canonicalSlip(String studentId, RoundView round, List<EnrollmentView> enrollments, Instant generatedAt) {
        StringBuilder body = new StringBuilder("CampusCore Registration Slip\n")
                .append("Student: ").append(slipValue(studentId)).append('\n')
                .append("Semester: ").append(slipValue(round.semesterId())).append('\n')
                .append("Registration round: ").append(slipValue(round.id())).append('\n')
                .append("Generated at: ").append(generatedAt).append('\n')
                .append("\nEnrollments\n");
        enrollments.stream()
                .sorted(Comparator.comparing((EnrollmentView e) -> slipValue(e.section().courseCode()))
                        .thenComparing(e -> slipValue(e.section().sectionNumber()))
                        .thenComparing(EnrollmentView::id))
                .forEach(enrollment -> appendEnrollment(body, enrollment));
        return body.toString();
    }

    private static void appendEnrollment(StringBuilder body, EnrollmentView enrollment) {
        SectionView section = enrollment.section();
        body.append("Course: ").append(slipValue(section.courseCode())).append(" - ")
                .append(slipValue(section.courseName())).append('\n')
                .append("Section: ").append(slipValue(section.sectionNumber())).append('\n')
                .append("Credits: ").append(section.credits()).append('\n')
                .append("Lecturer: ").append(slipValue(section.lecturerName())).append('\n')
                .append("Classroom: ").append(slipValue(section.classroom())).append('\n');
        section.schedules().stream()
                .sorted(Comparator.comparingInt(ScheduleView::dayOfWeek)
                        .thenComparing(ScheduleView::startTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ScheduleView::id))
                .forEach(schedule -> body.append("Schedule: day=").append(schedule.dayOfWeek())
                        .append(' ').append(schedule.startTime()).append('-').append(schedule.endTime())
                        .append(" room=").append(slipValue(schedule.building())).append(' ')
                        .append(slipValue(schedule.roomNumber())).append('\n'));
        body.append("Enrollment id: ").append(slipValue(enrollment.id())).append("\n\n");
    }

    private static boolean isActiveEnrollmentStatus(String status) {
        return status != null && Set.of("ACTIVE", "ENROLLED", "PENDING", "CONFIRMED").contains(status.toUpperCase(java.util.Locale.ROOT));
    }

    private static String slipValue(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').trim();
    }

    @Transactional(readOnly = true)
    public List<RoundView> adminRounds() { return roundRepository.findAll().stream().sorted(Comparator.comparing(RegistrationRoundEntity::getRegistrationStart).reversed()).map(this::roundView).toList(); }

    @Transactional
    public RoundView adminCreate(AdminRoundRequest request) {
        RegistrationRoundEntity round = RegistrationRoundEntity.create(UUID.randomUUID().toString(), request.semesterId(), "DRAFT",
                request.registrationStart(), request.registrationEnd(), request.addDropStart(), request.addDropEnd(), request.maxCredits(), request.institutionTimeZone());
        return roundView(roundRepository.saveAndFlush(round));
    }

    @Transactional
    public RoundView adminUpdate(String id, AdminRoundRequest request) {
        RegistrationRoundEntity round = roundRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "REGISTRATION_ROUND_NOT_FOUND", "Registration round not found"));
        requireVersion(round, request.version());
        round.update(round.getStatus(), request.registrationStart(), request.registrationEnd(), request.addDropStart(), request.addDropEnd(), request.maxCredits(), request.institutionTimeZone());
        try { return roundView(roundRepository.saveAndFlush(round)); } catch (ObjectOptimisticLockingFailureException e) { throw problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Registration round was changed by another administrator"); }
    }

    @Transactional
    public RoundView adminTransition(String id, String action, Long version) {
        RegistrationRoundEntity round = roundRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "REGISTRATION_ROUND_NOT_FOUND", "Registration round not found"));
        requireVersion(round, version);
        String status = switch (action.toLowerCase()) { case "open" -> "OPEN"; case "close" -> "CLOSED"; case "archive" -> "ARCHIVED"; default -> throw new RegistrationProblemException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unsupported round transition"); };
        round.update(status, round.getRegistrationStart(), round.getRegistrationEnd(), round.getAddDropStart(), round.getAddDropEnd(), round.getMaxCredits(), round.getInstitutionTimeZone());
        return roundView(roundRepository.saveAndFlush(round));
    }

    private static void requireVersion(RegistrationRoundEntity round, Long expected) { if (expected != null && expected.longValue() != round.getVersion()) throw problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Registration round version is stale"); }

    public record AdminRoundRequest(String semesterId, Instant registrationStart, Instant registrationEnd,
            Instant addDropStart, Instant addDropEnd, int maxCredits, String institutionTimeZone, Long version) { }
    public record DropResult(String enrollmentId, boolean replayed) { }
    public record SlipResult(byte[] pdf, String checksum) { }

    private List<String> validateViolations(String studentId, EnrollmentRequest request) {
        RoundView round = round(request.roundId());
        Instant now = clock.instant();
        List<String> v = new ArrayList<>();
        if (!"OPEN".equalsIgnoreCase(round.status()) || now.isBefore(round.registrationStart()) || now.isAfter(round.registrationEnd())) v.add("REGISTRATION_ROUND_CLOSED");
        Map<String, Object> section;
        try { section = jdbc.queryForMap("SELECT s.\"id\",s.\"courseId\",s.\"semesterId\",s.\"capacity\",s.\"enrolledCount\",s.\"status\" FROM academic.\"Section\" s WHERE s.\"id\"=:id", new MapSqlParameterSource("id", request.sectionId())); }
        catch (Exception e) { v.add("SECTION_NOT_OPEN"); return v; }
        if (!round.semesterId().equals(section.get("semesterId"))) v.add("SECTION_NOT_OPEN");
        if (!"OPEN".equalsIgnoreCase(String.valueOf(section.get("status")))) v.add("SECTION_NOT_OPEN");
        if (((Number) section.get("enrolledCount")).intValue() >= ((Number) section.get("capacity")).intValue()) v.add("SECTION_FULL");
        Long duplicate = jdbc.queryForObject("SELECT count(*) FROM academic.\"Enrollment\" e WHERE e.\"studentId\"=:studentId AND e.\"sectionId\"=:sectionId AND " + ACTIVE,
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("sectionId", request.sectionId()), Long.class);
        if (duplicate != null && duplicate > 0) v.add("ALREADY_ENROLLED");
        int credits = ((Number) jdbc.queryForObject("SELECT \"credits\" FROM academic.\"Course\" WHERE \"id\"=:id", new MapSqlParameterSource("id", section.get("courseId")), Integer.class)).intValue();
        if (selectedCredits(studentId, round.semesterId()) + credits > round.maxCredits()) v.add("CREDIT_LIMIT_EXCEEDED");
        List<String> required = jdbc.queryForList("SELECT r.\"requiredCourseId\" FROM academic.\"CourseRequirement\" r WHERE r.\"courseId\"=:courseId AND r.\"requirementType\"='PREREQUISITE'", new MapSqlParameterSource("courseId", section.get("courseId")), String.class);
        if (!required.isEmpty()) {
            Long completed = jdbc.queryForObject("SELECT count(*) FROM academic.\"Enrollment\" e JOIN academic.\"Section\" s ON s.\"id\"=e.\"sectionId\" WHERE e.\"studentId\"=:studentId AND s.\"courseId\" IN (:required) AND lower(e.\"status\")='completed'", new MapSqlParameterSource().addValue("studentId", studentId).addValue("required", required), Long.class);
            if (completed == null || completed < required.size()) v.add("PREREQUISITE_NOT_MET");
        }
        List<String> coreq = jdbc.queryForList("SELECT r.\"requiredCourseId\" FROM academic.\"CourseRequirement\" r WHERE r.\"courseId\"=:courseId AND r.\"requirementType\"='COREQUISITE'", new MapSqlParameterSource("courseId", section.get("courseId")), String.class);
        if (!coreq.isEmpty()) {
            Long present = jdbc.queryForObject("SELECT count(DISTINCT s.\"courseId\") FROM academic.\"Enrollment\" e JOIN academic.\"Section\" s ON s.\"id\"=e.\"sectionId\" WHERE e.\"studentId\"=:studentId AND s.\"courseId\" IN (:coreq) AND " + ACTIVE,
                    new MapSqlParameterSource().addValue("studentId", studentId).addValue("coreq", coreq), Long.class);
            if (present == null || present < coreq.size()) v.add("COREQUISITE_NOT_MET");
        }
        Long scheduleConflict = jdbc.queryForObject("SELECT count(*) FROM academic.\"SectionSchedule\" requested JOIN academic.\"SectionSchedule\" chosen ON chosen.\"dayOfWeek\"=requested.\"dayOfWeek\" AND requested.\"startTimeValue\" < chosen.\"endTimeValue\" AND chosen.\"startTimeValue\" < requested.\"endTimeValue\" JOIN academic.\"Enrollment\" e ON e.\"sectionId\"=chosen.\"sectionId\" WHERE requested.\"sectionId\"=:sectionId AND e.\"studentId\"=:studentId AND " + ACTIVE,
                new MapSqlParameterSource().addValue("sectionId", request.sectionId()).addValue("studentId", studentId), Long.class);
        if (scheduleConflict != null && scheduleConflict > 0) v.add("SCHEDULE_CONFLICT");
        return v;
    }

    private int selectedCredits(String studentId, String semesterId) {
        Integer value = jdbc.queryForObject("SELECT coalesce(sum(c.\"credits\"),0) FROM academic.\"Enrollment\" e JOIN academic.\"Section\" s ON s.\"id\"=e.\"sectionId\" JOIN academic.\"Course\" c ON c.\"id\"=s.\"courseId\" WHERE e.\"studentId\"=:studentId AND e.\"semesterId\"=:semesterId AND " + ACTIVE,
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId), Integer.class);
        return value == null ? 0 : value;
    }

    private EnrollmentView enrollment(Map<String, Object> row, String studentId) {
        SectionView section = sectionsForId(String.valueOf(row.get("sectionId")), studentId);
        Object at = row.get("enrolledAt");
        Instant enrolledAt = at instanceof Timestamp t ? t.toInstant() : at instanceof Instant i ? i : clock.instant();
        return new EnrollmentView(String.valueOf(row.get("id")), String.valueOf(row.get("sectionId")), String.valueOf(row.get("roundId")), String.valueOf(row.get("status")), enrolledAt, section);
    }

    private SectionView sectionsForId(String id, String studentId) { return jdbc.query("SELECT s.\"id\",s.\"courseId\",c.\"code\",c.\"name\",c.\"credits\",s.\"sectionNumber\",s.\"capacity\",s.\"enrolledCount\",s.\"status\",coalesce(trim(u.\"firstName\"||' '||u.\"lastName\"),'') lecturer_name,coalesce(cl.\"building\"||' '||cl.\"roomNumber\",'') classroom FROM academic.\"Section\" s JOIN academic.\"Course\" c ON c.\"id\"=s.\"courseId\" LEFT JOIN academic.\"Lecturer\" l ON l.\"id\"=s.\"lecturerId\" LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\"=l.\"userId\" LEFT JOIN academic.\"Classroom\" cl ON cl.\"id\"=s.\"classroomId\" WHERE s.\"id\"=:id", new MapSqlParameterSource("id", id), (rs,n)->section(rs,true)).stream().findFirst().orElseThrow(); }
    private SectionView section(ResultSet rs, boolean selected) throws java.sql.SQLException {
        String id = rs.getString("id");
        List<ScheduleView> schedules = jdbc.query("SELECT ss.\"id\",ss.\"dayOfWeek\",ss.\"startTimeValue\",ss.\"endTimeValue\",ss.\"classroomId\",cl.\"building\",cl.\"roomNumber\" FROM academic.\"SectionSchedule\" ss LEFT JOIN academic.\"Classroom\" cl ON cl.\"id\"=ss.\"classroomId\" WHERE ss.\"sectionId\"=:id ORDER BY ss.\"dayOfWeek\",ss.\"startTimeValue\"", new MapSqlParameterSource("id", id), (r,n)->new ScheduleView(r.getString("id"),r.getInt("dayOfWeek"),r.getObject("startTimeValue",LocalTime.class),r.getObject("endTimeValue",LocalTime.class),r.getString("classroomId"),r.getString("building"),r.getString("roomNumber")));
        int capacity = rs.getInt("capacity"), count = rs.getInt("enrolledCount");
        return new SectionView(id, rs.getString("courseId"), rs.getString("code"), rs.getString("name"), rs.getInt("credits"), rs.getString("sectionNumber"), rs.getString("lecturer_name"), rs.getString("classroom"), capacity, count, Math.max(0, capacity-count), rs.getString("status"), selected, schedules, List.of());
    }
    private RoundView lockRoundForMutation(String id) { RegistrationRoundEntity r = roundRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND,"REGISTRATION_ROUND_NOT_FOUND","Registration round not found")); return roundView(r); }
    private AcademicSectionEntity lockSection(String id) { return sectionRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND,"SECTION_NOT_OPEN","Section not found")); }
    private MutationLocks lockMutation(String roundId, String sectionId, String studentId) {
        RoundView round = lockRoundForMutation(roundId);
        AcademicSectionEntity section = lockSection(sectionId);
        lockStudentEnrollments(studentId, round.semesterId());
        return new MutationLocks(round, section);
    }
    private RoundView roundView(RegistrationRoundEntity r) { return new RoundView(r.getId(), r.getSemesterId(), r.getStatus(), r.getRegistrationStart(), r.getRegistrationEnd(), r.getAddDropStart(), r.getAddDropEnd(), clock.instant(), r.getInstitutionTimeZone(), r.getMaxCredits(), r.getVersion()); }
    private RoundView roundForSection(String sectionId) { return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\",r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\",r.\"version\" FROM academic.\"Section\" s JOIN academic.\"RegistrationRound\" r ON r.\"semesterId\"=s.\"semesterId\" WHERE s.\"id\"=:sectionId ORDER BY CASE WHEN upper(r.\"status\")='OPEN' THEN 0 ELSE 1 END,r.\"registrationStart\" DESC,r.\"id\"", new MapSqlParameterSource("sectionId", sectionId), roundMapper()).stream().findFirst().orElseThrow(() -> problem(HttpStatus.CONFLICT,"REGISTRATION_ROUND_CLOSED","No registration round found for section")); }
    private void requireStudent(String id) { if (id == null || id.isBlank()) throw problem(HttpStatus.FORBIDDEN,"STUDENT_PROFILE_REQUIRED","Student profile is required"); }
    private OperationReservation reserveOperation(String studentId, UUID key, String hash, String type) {
        boolean created = mutationGateway.insertOperationIfAbsent(UUID.randomUUID().toString(), studentId,
                key.toString(), hash, type, clock.instant());
        EnrollmentOperationEntity operation = operationRepository
                .findLockedByStudentIdAndIdempotencyKey(studentId, key.toString())
                .orElseThrow(() -> new IllegalStateException("Registration operation insert was not visible"));
        if (!hash.equals(operation.getCanonicalRequestHash()) || !type.equals(operation.getOperationType())) {
            throw problem(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key was used with a different payload");
        }
        if ("COMPLETED".equals(operation.getState())) {
            return new OperationReservation(operation, new ExistingOperation(operation.getId(), operation.getResponseBody()));
        }
        if (!created) {
            throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Request is still processing",true,List.of());
        }
        return new OperationReservation(operation, null);
    }
    private void completeOperation(EnrollmentOperationEntity operation, Object body, int status) { try { operation.complete(status, mapper.writeValueAsString(body), clock.instant()); operationRepository.save(operation); } catch(JsonProcessingException e){ throw new IllegalStateException(e); } }
    private void lockStudentEnrollments(String studentId, String semesterId) {
        List<String> lockedStudents = enrollmentRepository.findLockedStudent(studentId);
        if (lockedStudents == null || lockedStudents.isEmpty()) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        enrollmentRepository.findLockedStudentEnrollments(studentId, semesterId,
                List.of("ACTIVE", "ENROLLED", "PENDING", "CONFIRMED"));
    }
    private EnrollmentView replay(ExistingOperation existing, String studentId) { try { return mapper.readValue(existing.body(), EnrollmentView.class); } catch(Exception e){ throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Stored result could not be replayed",true,List.of()); } }
    private org.springframework.jdbc.core.RowMapper<RoundView> roundMapper() { return (rs,n)->new RoundView(rs.getString("id"),rs.getString("semesterId"),rs.getString("status"),rs.getTimestamp("registrationStart").toInstant(),rs.getTimestamp("registrationEnd").toInstant(),rs.getTimestamp("addDropStart").toInstant(),rs.getTimestamp("addDropEnd").toInstant(),clock.instant(),rs.getString("institutionTimeZone"),rs.getInt("maxCredits"),rs.getLong("version")); }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
    private static int decodeCursor(String c){if(c==null||c.isBlank())return 0;try{return Integer.parseInt(new String(Base64.getUrlDecoder().decode(c),StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalArgumentException("cursor is invalid");}}
    private static String encodeCursor(int n){return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(n).getBytes(StandardCharsets.UTF_8));}
    private static String canonicalHash(String op,String student,EnrollmentRequest r){return sha256(op+"|"+student+"|"+r.roundId().trim()+"|"+r.sectionId().trim());}
    private static String slipId(String studentId, String roundId) {
        return sha256("REGISTRATION_SLIP|" + studentId + "|" + roundId);
    }
    private static String sha256(String text){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sha256(byte[] value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    private static RegistrationProblemException problem(HttpStatus s,String c,String m){return new RegistrationProblemException(s,c,m);}
    private static RegistrationProblemException capacityProblem(String message) {
        String code = "SECTION_FULL".equals(message) ? "SECTION_FULL" : "SECTION_COUNT_INVARIANT";
        return new RegistrationProblemException(HttpStatus.CONFLICT, code, "Section capacity invariant rejected the mutation");
    }
    private static RegistrationProblemException rejection(String code,List<String> violations){return new RegistrationProblemException(HttpStatus.CONFLICT,code,"Registration rejected",false,violations.stream().map(v->new RegistrationProblemException.Violation(null,null,v)).toList());}
    private record ExistingOperation(String id,String body) { }
    private record OperationReservation(EnrollmentOperationEntity operation, ExistingOperation replay) { }
    private record MutationLocks(RoundView round, AcademicSectionEntity section) { }
    public record MutationResult(EnrollmentView enrollment, boolean replayed) { }
}
