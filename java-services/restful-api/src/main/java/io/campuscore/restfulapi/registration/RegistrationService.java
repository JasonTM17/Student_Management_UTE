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

    public RegistrationService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
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
        ExistingOperation existing = operation(studentId, key, hash);
        if (existing != null) return new MutationResult(replay(existing, studentId), true);
        createOperation(studentId, key, hash, "ENROLL");
        RoundView round = lockRoundForMutation(request.roundId());
        lockSection(request.sectionId());
        List<String> violations = validateViolations(studentId, request);
        if (!violations.isEmpty()) throw rejection(violations.get(0), violations);
        Instant now = clock.instant();
        String enrollmentId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academic.\"Enrollment\" (\"id\",\"studentId\",\"sectionId\",\"semesterId\",\"status\",\"enrolledAt\",\"gradeStatus\") VALUES (:id,:studentId,:sectionId,:semesterId,'ENROLLED',:now,'NOT_GRADED')",
                new MapSqlParameterSource().addValue("id", enrollmentId).addValue("studentId", studentId).addValue("sectionId", request.sectionId()).addValue("semesterId", round.semesterId()).addValue("now", Timestamp.from(now)));
        jdbc.update("UPDATE academic.\"Section\" SET \"enrolledCount\"=\"enrolledCount\"+1,\"updatedAt\"=:now WHERE \"id\"=:id",
                new MapSqlParameterSource().addValue("id", request.sectionId()).addValue("now", Timestamp.from(now)));
        EnrollmentView persisted = enrollment(Map.of("id", enrollmentId, "sectionId", request.sectionId(), "semesterId", round.semesterId(), "status", "ENROLLED", "enrolledAt", now), studentId);
        EnrollmentView response = new EnrollmentView(persisted.id(), persisted.sectionId(), request.roundId(), persisted.status(), persisted.enrolledAt(), persisted.section());
        completeOperation(studentId, key, response, 201);
        jdbc.update("INSERT INTO academic.\"EnrollmentAudit\" (\"id\",\"studentId\",\"sectionId\",\"action\",\"reasonCode\") VALUES (:id,:studentId,:sectionId,'ENROLL','ENROLLED')",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString()).addValue("studentId", studentId).addValue("sectionId", request.sectionId()));
        return new MutationResult(response, false);
    }

    @Transactional
    public void drop(String studentId, String enrollmentId, UUID key) {
        requireStudent(studentId);
        Map<String, Object> row = jdbc.queryForMap("SELECT e.\"id\",e.\"studentId\",e.\"sectionId\",e.\"semesterId\",e.\"status\" FROM academic.\"Enrollment\" e WHERE e.\"id\"=:id FOR UPDATE", new MapSqlParameterSource("id", enrollmentId));
        if (!studentId.equals(row.get("studentId")) && !studentId.equals(row.get("student_id"))) throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_NOT_OWNER", "Enrollment does not belong to the current student");
        String hash = sha256("DROP|" + studentId + "|" + enrollmentId);
        ExistingOperation existing = operation(studentId, key, hash);
        if (existing != null) return;
        createOperation(studentId, key, hash, "DROP");
        RoundView round = roundForSemester(String.valueOf(row.get("semesterId")));
        Instant now = clock.instant();
        if (now.isAfter(round.addDropEnd()) || now.isBefore(round.addDropStart())) throw problem(HttpStatus.CONFLICT, "ADD_DROP_CLOSED", "Add/drop window is closed");
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='DROPPED',\"droppedAt\"=:now,\"updatedAt\"=:now WHERE \"id\"=:id", new MapSqlParameterSource().addValue("id", enrollmentId).addValue("now", Timestamp.from(now)));
        jdbc.update("UPDATE academic.\"Section\" SET \"enrolledCount\"=GREATEST(0,\"enrolledCount\"-1),\"updatedAt\"=:now WHERE \"id\"=:id", new MapSqlParameterSource().addValue("id", row.get("sectionId")).addValue("now", Timestamp.from(now)));
        completeOperation(studentId, key, Map.of("dropped", true, "enrollmentId", enrollmentId), 200);
    }

    public byte[] slip(String studentId, String roundId) {
        RoundView round = round(roundId);
        List<EnrollmentView> enrollments = enrollments(studentId, round.semesterId(), null, MAX_LIMIT).items();
        StringBuilder canonical = new StringBuilder("CampusCore Registration Slip\n").append(round.id()).append('\n');
        enrollments.stream().sorted(Comparator.comparing(e -> e.section().courseCode())).forEach(e -> canonical.append(e.id()).append('|').append(e.section().courseCode()).append('|').append(e.section().sectionNumber()).append('|').append(e.section().credits()).append('\n'));
        String hash = sha256(canonical.toString());
        return SimplePdf.render(canonical + "SHA-256: " + hash + "\n");
    }

    private List<String> validateViolations(String studentId, EnrollmentRequest request) {
        RoundView round = round(request.roundId());
        Instant now = clock.instant();
        List<String> v = new ArrayList<>();
        if (now.isBefore(round.registrationStart()) || now.isAfter(round.registrationEnd())) v.add("REGISTRATION_ROUND_CLOSED");
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
    private RoundView lockRoundForMutation(String id) { return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\",r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\" FROM academic.\"RegistrationRound\" r WHERE r.\"id\"=:id FOR UPDATE", new MapSqlParameterSource("id", id), roundMapper()).stream().findFirst().orElseThrow(() -> problem(HttpStatus.NOT_FOUND,"REGISTRATION_ROUND_NOT_FOUND","Registration round not found")); }
    private void lockSection(String id) { jdbc.queryForMap("SELECT \"id\" FROM academic.\"Section\" WHERE \"id\"=:id FOR UPDATE", new MapSqlParameterSource("id", id)); }
    private RoundView roundForSemester(String semester) { return jdbc.query("SELECT r.\"id\",r.\"semesterId\",r.\"status\",r.\"registrationStart\",r.\"registrationEnd\",r.\"addDropStart\",r.\"addDropEnd\",r.\"maxCredits\",r.\"institutionTimeZone\" FROM academic.\"RegistrationRound\" r WHERE r.\"semesterId\"=:semester ORDER BY r.\"registrationStart\" DESC", new MapSqlParameterSource("semester", semester), roundMapper()).stream().findFirst().orElseThrow(() -> problem(HttpStatus.CONFLICT,"REGISTRATION_ROUND_CLOSED","No registration round found")); }
    private void requireStudent(String id) { if (id == null || id.isBlank()) throw problem(HttpStatus.FORBIDDEN,"STUDENT_PROFILE_REQUIRED","Student profile is required"); }
    private ExistingOperation operation(String studentId, UUID key, String hash) { List<Map<String,Object>> rows=jdbc.queryForList("SELECT \"id\",\"canonicalRequestHash\",\"state\",\"responseBody\" FROM academic.\"EnrollmentOperation\" WHERE \"studentId\"=:studentId AND \"idempotencyKey\"=:key",new MapSqlParameterSource().addValue("studentId",studentId).addValue("key",key.toString())); if(rows.isEmpty())return null; Map<String,Object> r=rows.get(0); if(!hash.equals(r.get("canonicalRequestHash")))throw problem(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key was used with a different payload"); if("COMPLETED".equals(r.get("state")))return new ExistingOperation(String.valueOf(r.get("id")),String.valueOf(r.get("responseBody"))); throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Request is still processing",true,List.of()); }
    private void createOperation(String studentId, UUID key, String hash, String type) { try { jdbc.update("INSERT INTO academic.\"EnrollmentOperation\" (\"id\",\"studentId\",\"idempotencyKey\",\"canonicalRequestHash\",\"operationType\",\"state\") VALUES (:id,:studentId,:key,:hash,:type,'PROCESSING')",new MapSqlParameterSource().addValue("id",UUID.randomUUID().toString()).addValue("studentId",studentId).addValue("key",key.toString()).addValue("hash",hash).addValue("type",type)); } catch(DataIntegrityViolationException e){ throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Request is still processing",true,List.of()); } }
    private void completeOperation(String studentId, UUID key, Object body, int status) { try { jdbc.update("UPDATE academic.\"EnrollmentOperation\" SET \"state\"='COMPLETED',\"responseStatus\"=:status,\"responseBody\"=:body,\"completedAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"studentId\"=:studentId AND \"idempotencyKey\"=:key",new MapSqlParameterSource().addValue("studentId",studentId).addValue("key",key.toString()).addValue("status",status).addValue("body",mapper.writeValueAsString(body))); } catch(JsonProcessingException e){ throw new IllegalStateException(e); } }
    private EnrollmentView replay(ExistingOperation existing, String studentId) { try { return mapper.readValue(existing.body(), EnrollmentView.class); } catch(Exception e){ throw new RegistrationProblemException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","Stored result could not be replayed",true,List.of()); } }
    private org.springframework.jdbc.core.RowMapper<RoundView> roundMapper() { return (rs,n)->new RoundView(rs.getString("id"),rs.getString("semesterId"),rs.getString("status"),rs.getTimestamp("registrationStart").toInstant(),rs.getTimestamp("registrationEnd").toInstant(),rs.getTimestamp("addDropStart").toInstant(),rs.getTimestamp("addDropEnd").toInstant(),clock.instant(),rs.getString("institutionTimeZone"),rs.getInt("maxCredits")); }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
    private static int decodeCursor(String c){if(c==null||c.isBlank())return 0;try{return Integer.parseInt(new String(Base64.getUrlDecoder().decode(c),StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalArgumentException("cursor is invalid");}}
    private static String encodeCursor(int n){return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(n).getBytes(StandardCharsets.UTF_8));}
    private static String canonicalHash(String op,String student,EnrollmentRequest r){return sha256(op+"|"+student+"|"+r.roundId().trim()+"|"+r.sectionId().trim());}
    private static String sha256(String text){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static RegistrationProblemException problem(HttpStatus s,String c,String m){return new RegistrationProblemException(s,c,m);}
    private static RegistrationProblemException rejection(String code,List<String> violations){return new RegistrationProblemException(HttpStatus.CONFLICT,code,"Registration rejected",false,violations.stream().map(v->new RegistrationProblemException.Violation(null,null,v)).toList());}
    private record ExistingOperation(String id,String body) { }
    public record MutationResult(EnrollmentView enrollment, boolean replayed) { }
}
