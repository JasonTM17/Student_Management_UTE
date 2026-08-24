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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Registration orchestration. Database locks are acquired in round then section order. */
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

    public RegistrationService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, Clock clock,
            RegistrationRoundRepository roundRepository, AcademicSectionRepository sectionRepository,
            EnrollmentRepository enrollmentRepository, EnrollmentOperationRepository operationRepository,
            EnrollmentAuditRepository auditRepository, RegistrationSlipRepository slipRepository) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.roundRepository = roundRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.operationRepository = operationRepository;
        this.auditRepository = auditRepository;
        this.slipRepository = slipRepository;
    }

    public RoundPage rounds(String semesterId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit <= 0 ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        int offset = decodeCursor(cursor);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("limit", safeLimit + 1).addValue("offset", offset);
        String sql = "SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\","
                + "r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\" "
                + "FROM academic.\"RegistrationRound\" r" + (semesterId == null ? "" : " WHERE r.\"semesterId\"=:semesterId")
                + " ORDER BY r.\"registrationStart\" DESC,r.\"id\" LIMIT :limit OFFSET :offset";
        if (semesterId != null) p.addValue("semesterId", semesterId.trim());
        List<RoundView> rows = jdbc.query(sql, p, roundMapper());
        String next = rows.size() > safeLimit ? encodeCursor(offset + safeLimit) : null;
        return new RoundPage(rows.subList(0, Math.min(rows.size(), safeLimit)), next);
    }

    public RoundView round(String id) {
        return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\","
                + "r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\" FROM academic.\"RegistrationRound\" r WHERE r.\"id\"=:id",
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
                + "LEFT JOIN academic.\"Lecturer\" l ON l.\"id\"=s.\"lecturerId\" LEFT JOIN auth.\"User\" u ON u.\"id\"=l.\"userId\" "
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT e.\"id\",e.\"sectionId\",e.\"semesterId\",e.\"status\",e.\"enrolledAt\" FROM academic.\"Enrollment\" e WHERE e.\"studentId\"=:studentId"
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
        RoundView round = lockRoundForMutation(request.roundId());
        AcademicSectionEntity lockedSection = lockSection(request.sectionId());
        lockStudentEnrollments(studentId, round.semesterId());
        ExistingOperation existing = operation(studentId, key, hash);
        if (existing != null) return new MutationResult(replay(existing, studentId), true);
        EnrollmentOperationEntity operation = createOperation(studentId, key, hash, "ENROLL");
        List<String> violations = validateViolations(studentId, request);
        if (!violations.isEmpty()) throw rejection(violations.get(0), violations);
        Instant now = clock.instant();
        String enrollmentId = UUID.randomUUID().toString();
        enrollmentRepository.save(EnrollmentEntity.enrolled(enrollmentId, studentId, request.sectionId(), round.semesterId(), now));
        lockedSection.incrementEnrollment();
        sectionRepository.saveAndFlush(lockedSection);
        EnrollmentView persisted = enrollment(Map.of("id", enrollmentId, "sectionId", request.sectionId(), "semesterId", round.semesterId(), "status", "ENROLLED", "enrolledAt", now), studentId);
        EnrollmentView response = new EnrollmentView(persisted.id(), persisted.sectionId(), request.roundId(), persisted.status(), persisted.enrolledAt(), persisted.section());
        completeOperation(operation, response, 201);
        auditRepository.save(EnrollmentAuditEntity.of(UUID.randomUUID().toString(), operation.getId(), studentId, request.sectionId(), "ENROLL", "ENROLLED", now));
        return new MutationResult(response, false);
    }

    @Transactional
    public void drop(String studentId, String enrollmentId, UUID key) {
        requireStudent(studentId);
        EnrollmentEntity previewEnrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (!studentId.equals(previewEnrollment.getStudentId())) throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_NOT_OWNER", "Enrollment does not belong to the current student");
        String hash = sha256("DROP|" + studentId + "|" + enrollmentId);
        RoundView candidateRound = roundForSemester(previewEnrollment.getSemesterId());
        RoundView round = lockRoundForMutation(candidateRound.id());
        AcademicSectionEntity lockedSection = lockSection(previewEnrollment.getSectionId());
        lockStudentEnrollments(studentId, previewEnrollment.getSemesterId());
        ExistingOperation existing = operation(studentId, key, hash);
        if (existing != null) return;
        EnrollmentOperationEntity operation = createOperation(studentId, key, hash, "DROP");
        EnrollmentEntity lockedEnrollment = enrollmentRepository.findLockedById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        Instant now = clock.instant();
        if (now.isAfter(round.addDropEnd()) || now.isBefore(round.addDropStart())) throw problem(HttpStatus.CONFLICT, "ADD_DROP_CLOSED", "Add/drop window is closed");
        lockedEnrollment.markDropped(now);
        enrollmentRepository.save(lockedEnrollment);
        lockedSection.decrementEnrollment();
        sectionRepository.saveAndFlush(lockedSection);
        completeOperation(operation, Map.of("dropped", true, "enrollmentId", enrollmentId), 200);
        auditRepository.save(EnrollmentAuditEntity.of(UUID.randomUUID().toString(), operation.getId(), studentId, lockedEnrollment.getSectionId(), "DROP", "DROPPED", now));
    }

    public byte[] slip(String studentId, String roundId) {
        RoundView round = round(roundId);
        List<EnrollmentView> enrollments = enrollments(studentId, round.semesterId(), null, MAX_LIMIT).items();
        StringBuilder canonical = new StringBuilder("CampusCore Registration Slip\n").append(round.id()).append('\n');
        enrollments.stream().sorted(Comparator.comparing(e -> e.section().courseCode())).forEach(e -> canonical.append(e.id()).append('|').append(e.section().courseCode()).append('|').append(e.section().sectionNumber()).append('|').append(e.section().credits()).append('\n'));
        String hash = sha256(canonical.toString());
        byte[] pdf = SimplePdf.render(canonical + "SHA-256: " + hash + "\n");
        String renderedHash = sha256Bytes(pdf);
        if (slipRepository.findByStudentIdAndRoundId(studentId, roundId).isEmpty()) {
            slipRepository.save(RegistrationSlipEntity.snapshot(studentId + "-" + roundId, studentId, roundId, renderedHash, clock.instant()));
        }
        return pdf;
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
        return new EnrollmentView(String.valueOf(row.get("id")), String.valueOf(row.get("sectionId")), String.valueOf(row.get("semesterId")), String.valueOf(row.get("status")), enrolledAt, section);
    }

    private SectionView sectionsForId(String id, String studentId) { return jdbc.query("SELECT s.\"id\",s.\"courseId\",c.\"code\",c.\"name\",c.\"credits\",s.\"sectionNumber\",s.\"capacity\",s.\"enrolledCount\",s.\"status\",'' lecturer_name,'' classroom FROM academic.\"Section\" s JOIN academic.\"Course\" c ON c.\"id\"=s.\"courseId\" WHERE s.\"id\"=:id", new MapSqlParameterSource("id", id), (rs,n)->section(rs,true)).stream().findFirst().orElseThrow(); }
    private SectionView section(ResultSet rs, boolean selected) throws java.sql.SQLException {
        String id = rs.getString("id");
        List<ScheduleView> schedules = jdbc.query("SELECT ss.\"id\",ss.\"dayOfWeek\",ss.\"startTimeValue\",ss.\"endTimeValue\",ss.\"classroomId\",cl.\"building\",cl.\"roomNumber\" FROM academic.\"SectionSchedule\" ss JOIN academic.\"Classroom\" cl ON cl.\"id\"=ss.\"classroomId\" WHERE ss.\"sectionId\"=:id ORDER BY ss.\"dayOfWeek\",ss.\"startTimeValue\"", new MapSqlParameterSource("id", id), (r,n)->new ScheduleView(r.getString("id"),r.getInt("dayOfWeek"),r.getObject("startTimeValue",LocalTime.class),r.getObject("endTimeValue",LocalTime.class),r.getString("classroomId"),r.getString("building"),r.getString("roomNumber")));
        int capacity = rs.getInt("capacity"), count = rs.getInt("enrolledCount");
        return new SectionView(id, rs.getString("courseId"), rs.getString("code"), rs.getString("name"), rs.getInt("credits"), rs.getString("sectionNumber"), rs.getString("lecturer_name"), rs.getString("classroom"), capacity, count, Math.max(0, capacity-count), rs.getString("status"), selected, schedules, List.of());
    }
    private RoundView lockRoundForMutation(String id) { RegistrationRoundEntity r = roundRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND,"REGISTRATION_ROUND_NOT_FOUND","Registration round not found")); return roundView(r); }
    private AcademicSectionEntity lockSection(String id) { return sectionRepository.findLockedById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND,"SECTION_NOT_OPEN","Section not found")); }
    private RoundView roundView(RegistrationRoundEntity r) { return new RoundView(r.getId(), r.getSemesterId(), r.getStatus(), r.getRegistrationStart(), r.getRegistrationEnd(), r.getAddDropStart(), r.getAddDropEnd(), clock.instant(), r.getInstitutionTimeZone(), r.getMaxCredits()); }
    private RoundView roundForSemester(String semester) { return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\",r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\" FROM academic.\"RegistrationRound\" r WHERE r.\"semesterId\"=:semester ORDER BY r.\"registrationStart\" DESC", new MapSqlParameterSource("semester", semester), roundMapper()).stream().findFirst().orElseThrow(() -> problem(HttpStatus.CONFLICT,"REGISTRATION_ROUND_CLOSED","No registration round found")); }
    private void requireStudent(String id) { if (id == null || id.isBlank()) throw problem(HttpStatus.FORBIDDEN,"STUDENT_PROFILE_REQUIRED","Student profile is required"); }
    private ExistingOperation operation(String studentId, UUID key, String hash) { EnrollmentOperationEntity op = operationRepository.findLockedByStudentIdAndIdempotencyKey(studentId, key.toString()).orElse(null); if(op==null)return null; if(!hash.equals(op.getCanonicalRequestHash()))throw problem(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key was used with a different payload"); if("COMPLETED".equals(op.getState()))return new ExistingOperation(op.getId(),op.getResponseBody()); throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Request is still processing",true,List.of()); }
    private EnrollmentOperationEntity createOperation(String studentId, UUID key, String hash, String type) { try { EnrollmentOperationEntity op = EnrollmentOperationEntity.processing(UUID.randomUUID().toString(), studentId, key.toString(), hash, type, clock.instant()); operationRepository.saveAndFlush(op); return op; } catch(DataIntegrityViolationException e){ throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Request is still processing",true,List.of()); } }
    private void completeOperation(EnrollmentOperationEntity operation, Object body, int status) { try { operation.complete(status, mapper.writeValueAsString(body), clock.instant()); operationRepository.save(operation); } catch(JsonProcessingException e){ throw new IllegalStateException(e); } }
    private void lockStudentEnrollments(String studentId, String semesterId) { enrollmentRepository.findLockedStudentEnrollments(studentId, semesterId, List.of("ACTIVE", "ENROLLED", "PENDING", "CONFIRMED")); }
    private EnrollmentView replay(ExistingOperation existing, String studentId) { try { return mapper.readValue(existing.body(), EnrollmentView.class); } catch(Exception e){ throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Stored result could not be replayed",true,List.of()); } }
    private org.springframework.jdbc.core.RowMapper<RoundView> roundMapper() { return (rs,n)->new RoundView(rs.getString("id"),rs.getString("semesterId"),rs.getString("status"),rs.getTimestamp("registrationStart").toInstant(),rs.getTimestamp("registrationEnd").toInstant(),rs.getTimestamp("addDropStart").toInstant(),rs.getTimestamp("addDropEnd").toInstant(),clock.instant(),rs.getString("institutionTimeZone"),rs.getInt("maxCredits")); }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
    private static int decodeCursor(String c){if(c==null||c.isBlank())return 0;try{return Integer.parseInt(new String(Base64.getUrlDecoder().decode(c),StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalArgumentException("cursor is invalid");}}
    private static String encodeCursor(int n){return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(n).getBytes(StandardCharsets.UTF_8));}
    private static String canonicalHash(String op,String student,EnrollmentRequest r){return sha256(op+"|"+student+"|"+r.roundId().trim()+"|"+r.sectionId().trim());}
    private static String sha256(String text){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sha256Bytes(byte[] bytes){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static RegistrationProblemException problem(HttpStatus s,String c,String m){return new RegistrationProblemException(s,c,m);}
    private static RegistrationProblemException rejection(String code,List<String> violations){return new RegistrationProblemException(HttpStatus.CONFLICT,code,"Registration rejected",false,violations.stream().map(v->new RegistrationProblemException.Violation(null,null,v)).toList());}
    private record ExistingOperation(String id,String body) { }
    public record MutationResult(EnrollmentView enrollment, boolean replayed) { }
}
