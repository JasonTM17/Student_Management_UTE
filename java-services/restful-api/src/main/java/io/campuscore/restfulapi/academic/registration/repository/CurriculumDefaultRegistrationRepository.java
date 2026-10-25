package io.campuscore.restfulapi.academic.registration.repository;

import static io.campuscore.restfulapi.academic.registration.model.CurriculumDefaultRegistrationRows.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.campuscore.restfulapi.academic.model.SemesterLifecycleStatuses;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** All SQL for curriculum default registration is kept at the repository boundary. */
@Repository
@Profile("persistence")
public class CurriculumDefaultRegistrationRepository {
    private static final String STUDENT = "academic.\"Student\"";
    private static final String ACADEMIC_YEAR = "academic.\"AcademicYear\"";
    private static final String SEMESTER = "academic.\"Semester\"";
    private static final String ROUND = "academic.\"RegistrationRound\"";
    private static final String ROUND_COHORT = "academic.\"RegistrationRoundCohort\"";
    private static final String CURRICULUM_COURSE = "academic.\"CurriculumCourse\"";
    private static final String COURSE = "academic.\"Course\"";
    private static final String SECTION = "academic.\"Section\"";
    private static final String SECTION_SCHEDULE = "academic.\"SectionSchedule\"";
    private static final String ENROLLMENT = "academic.\"Enrollment\"";
    private static final String ADMIN_AUDIT = "campuscore_audit.\"AdminAudit\"";

    private final NamedParameterJdbcTemplate jdbc;

    public CurriculumDefaultRegistrationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StudentRow> findStudent(String studentId) {
        List<StudentRow> rows = jdbc.query(
                "SELECT \"id\", \"curriculumId\", \"year\", \"admissionDate\", \"status\" FROM " + STUDENT
                        + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", studentId),
                (rs, rowNum) -> new StudentRow(rs.getString("id"), rs.getString("curriculumId"),
                        (Integer) rs.getObject("year"), instant(rs, "admissionDate"), rs.getString("status")));
        return rows.stream().findFirst();
    }

    public Optional<SemesterRow> findSemester(String semesterId) {
        List<SemesterRow> rows = jdbc.query(semesterSelect() + " WHERE semester.\"id\" = :id",
                new MapSqlParameterSource("id", semesterId), CurriculumDefaultRegistrationRepository::mapSemester);
        return rows.stream().findFirst();
    }

    public Optional<SemesterRow> findCurrentSemester(Instant now) {
        List<SemesterRow> rows = jdbc.query(semesterSelect()
                        + " WHERE semester.\"status\" IN (:openStatuses)"
                        + " AND semester.\"startDate\" <= :now AND semester.\"endDate\" >= :now"
                        + " ORDER BY semester.\"startDate\" DESC, semester.\"id\" LIMIT 1",
                params().addValue("now", Timestamp.from(now)).addValue("openStatuses", SemesterLifecycleStatuses.OPEN),
                CurriculumDefaultRegistrationRepository::mapSemester);
        return rows.stream().findFirst();
    }

    public Optional<SemesterRow> findSemesterForOpenRound(Instant now) {
        List<SemesterRow> rows = jdbc.query(
                semesterSelect() + " JOIN " + ROUND + " round ON round.\"semesterId\" = semester.\"id\""
                        + " WHERE round.\"status\" = 'OPEN' AND round.\"windowStart\" <= :now"
                        + " AND round.\"windowEnd\" >= :now AND semester.\"status\" IN (:openStatuses)"
                        + " ORDER BY CASE round.\"kind\" WHEN 'REGISTRATION' THEN 0 ELSE 1 END,"
                        + " round.\"windowStart\", semester.\"id\" LIMIT 1",
                params().addValue("now", Timestamp.from(now)).addValue("openStatuses", SemesterLifecycleStatuses.OPEN),
                CurriculumDefaultRegistrationRepository::mapSemester);
        return rows.stream().findFirst();
    }

    public List<AcademicYearRow> findAcademicYearsContaining(Instant admissionDate) {
        return jdbc.query("SELECT \"id\", \"year\", \"startDate\", \"endDate\" FROM " + ACADEMIC_YEAR
                        + " WHERE \"startDate\" <= :admissionDate AND :admissionDate < \"endDate\" ORDER BY \"year\", \"id\"",
                params().addValue("admissionDate", Timestamp.from(admissionDate)),
                (rs, rowNum) -> new AcademicYearRow(rs.getString("id"), rs.getInt("year"),
                        instant(rs, "startDate"), instant(rs, "endDate")));
    }

    public Optional<RoundRow> findOpenRound(String semesterId, Instant now) {
        List<RoundRow> rows = jdbc.query(roundSelect() + " WHERE round.\"semesterId\" = :semesterId"
                        + " AND round.\"status\" = 'OPEN' AND round.\"windowStart\" <= :now"
                        + " AND round.\"windowEnd\" >= :now"
                        + " ORDER BY CASE round.\"kind\" WHEN 'REGISTRATION' THEN 0 ELSE 1 END, round.\"windowStart\" LIMIT 1",
                params().addValue("semesterId", semesterId).addValue("now", Timestamp.from(now)),
                CurriculumDefaultRegistrationRepository::mapRound);
        return rows.stream().findFirst();
    }

    public Optional<RoundRow> findRound(String roundId) {
        List<RoundRow> rows = jdbc.query(roundSelect() + " WHERE round.\"id\" = :roundId",
                new MapSqlParameterSource("roundId", roundId), CurriculumDefaultRegistrationRepository::mapRound);
        return rows.stream().findFirst();
    }

    public Optional<RoundRow> findRoundForSemester(String semesterId) {
        List<RoundRow> rows = jdbc.query(roundSelect() + " WHERE round.\"semesterId\" = :semesterId"
                        + " ORDER BY round.\"windowStart\" DESC, round.\"id\" LIMIT 1",
                new MapSqlParameterSource("semesterId", semesterId), CurriculumDefaultRegistrationRepository::mapRound);
        return rows.stream().findFirst();
    }

    public List<CourseRow> findMandatoryCourses(String curriculumId, int year, int semester) {
        return jdbc.query("SELECT course.\"id\", course.\"code\", course.\"name\", course.\"nameEn\","
                        + " course.\"nameVi\", course.\"credits\" FROM " + CURRICULUM_COURSE + " curriculum_course"
                        + " JOIN " + COURSE + " course ON course.\"id\" = curriculum_course.\"courseId\""
                        + " WHERE curriculum_course.\"curriculumId\" = :curriculumId AND curriculum_course.\"year\" = :year"
                        + " AND curriculum_course.\"semester\" = :semester AND curriculum_course.\"isMandatory\" = TRUE"
                        + " ORDER BY course.\"code\", course.\"id\"",
                params().addValue("curriculumId", curriculumId).addValue("year", year).addValue("semester", semester),
                (rs, rowNum) -> new CourseRow(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("nameEn"), rs.getString("nameVi"), rs.getInt("credits")));
    }

    public List<String> findPassedCourseIds(String studentId) {
        return jdbc.query("SELECT DISTINCT \"courseId\" FROM " + ENROLLMENT
                        + " WHERE \"studentId\" = :studentId AND \"status\" = 'COMPLETED'"
                        + " AND \"gradeStatus\" IN ('PUBLISHED', 'APPEALED') AND \"letterGrade\" IS NOT NULL"
                        + " AND UPPER(\"letterGrade\") NOT IN ('F', 'W')",
                new MapSqlParameterSource("studentId", studentId), (rs, rowNum) -> rs.getString(1));
    }

    public List<EnrollmentState> findEnrollmentStates(String studentId, String semesterId) {
        return jdbc.query("SELECT \"courseId\", \"status\", \"registrationSource\" FROM " + ENROLLMENT
                        + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId"
                        + " ORDER BY \"createdAt\", \"id\"",
                params().addValue("studentId", studentId).addValue("semesterId", semesterId),
                (rs, rowNum) -> new EnrollmentState(rs.getString("courseId"), rs.getString("status"),
                        rs.getString("registrationSource")));
    }

    public List<SectionRow> findSections(String courseId, String semesterId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT section.\"id\", section.\"sectionNumber\","
                        + " section.\"capacity\", section.\"enrolledCount\", section.\"status\","
                        + " schedule.\"dayOfWeek\", schedule.\"startTime\", schedule.\"endTime\" FROM " + SECTION + " section"
                        + " LEFT JOIN " + SECTION_SCHEDULE + " schedule ON schedule.\"sectionId\" = section.\"id\""
                        + " WHERE section.\"courseId\" = :courseId AND section.\"semesterId\" = :semesterId"
                        + " ORDER BY section.\"sectionNumber\", section.\"id\", schedule.\"dayOfWeek\", schedule.\"startTime\"",
                params().addValue("courseId", courseId).addValue("semesterId", semesterId));
        Map<String, SectionBuilder> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = String.valueOf(row.get("id"));
            SectionBuilder section = grouped.computeIfAbsent(id, ignored -> new SectionBuilder(
                    id, String.valueOf(row.get("sectionNumber")), number(row.get("capacity")),
                    number(row.get("enrolledCount")), String.valueOf(row.get("status"))));
            if (row.get("dayOfWeek") != null) {
                section.schedules.add(new ScheduleRow(number(row.get("dayOfWeek")),
                        String.valueOf(row.get("startTime")), String.valueOf(row.get("endTime"))));
            }
        }
        return grouped.values().stream().map(SectionBuilder::build).toList();
    }

    public List<ScheduleRow> findActiveSchedules(String studentId, String semesterId) {
        return jdbc.query("SELECT schedule.\"dayOfWeek\", schedule.\"startTime\", schedule.\"endTime\" FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION_SCHEDULE + " schedule ON schedule.\"sectionId\" = enrollment.\"sectionId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " ORDER BY schedule.\"dayOfWeek\", schedule.\"startTime\"",
                params().addValue("studentId", studentId).addValue("semesterId", semesterId),
                (rs, rowNum) -> new ScheduleRow(rs.getInt("dayOfWeek"), rs.getString("startTime"), rs.getString("endTime")));
    }

    public List<String> findActiveSectionIds(String studentId, String semesterId) {
        return jdbc.query("SELECT enrollment.\"sectionId\" FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" <> 'CANCELLED' ORDER BY enrollment.\"sectionId\"",
                params().addValue("studentId", studentId).addValue("semesterId", semesterId),
                (rs, rowNum) -> rs.getString(1));
    }

    public List<StudentCursorRow> findStudentsForRound(String roundId, String cursor, int limit) {
        String cursorFilter = cursor == null || cursor.isBlank() ? "" : " AND student.\"id\" > :cursor";
        MapSqlParameterSource parameters = params().addValue("roundId", roundId).addValue("limit", limit);
        if (!cursorFilter.isEmpty()) {
            parameters.addValue("cursor", cursor);
        }
        return jdbc.query("SELECT student.\"id\", student.\"curriculumId\", student.\"year\", student.\"admissionDate\" FROM " + STUDENT + " student"
                        + " WHERE student.\"status\" = 'ACTIVE'" + cursorFilter
                        + " AND (NOT EXISTS (SELECT 1 FROM " + ROUND_COHORT + " cohort WHERE cohort.\"roundId\" = :roundId)"
                        + " OR EXISTS (SELECT 1 FROM " + ROUND_COHORT + " cohort WHERE cohort.\"roundId\" = :roundId"
                        + " AND (cohort.\"curriculumId\" IS NULL OR cohort.\"curriculumId\" = student.\"curriculumId\")"
                        + " AND (cohort.\"year\" IS NULL OR cohort.\"year\" = student.\"year\")))"
                        + " ORDER BY student.\"id\" LIMIT :limit",
                parameters, (rs, rowNum) -> new StudentCursorRow(rs.getString("id"), rs.getString("curriculumId"),
                        (Integer) rs.getObject("year"), instant(rs, "admissionDate")));
    }

    public boolean hasStudentsAfter(String roundId, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return false;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + STUDENT + " student WHERE student.\"status\" = 'ACTIVE'"
                        + " AND student.\"id\" > :cursor AND (NOT EXISTS (SELECT 1 FROM " + ROUND_COHORT
                        + " cohort WHERE cohort.\"roundId\" = :roundId) OR EXISTS (SELECT 1 FROM " + ROUND_COHORT
                        + " cohort WHERE cohort.\"roundId\" = :roundId AND (cohort.\"curriculumId\" IS NULL"
                        + " OR cohort.\"curriculumId\" = student.\"curriculumId\") AND (cohort.\"year\" IS NULL"
                        + " OR cohort.\"year\" = student.\"year\")))",
                params().addValue("roundId", roundId).addValue("cursor", cursor), Long.class);
        return count != null && count > 0;
    }

    public List<AdminAuditRow> findRecentRuns(int limit) {
        return jdbc.query("SELECT \"id\", \"actorLabel\", \"summary\", \"afterState\", \"createdAt\" FROM " + ADMIN_AUDIT
                        + " WHERE \"action\" = 'CURRICULUM_DEFAULT_REGISTRATION_APPLY'"
                        + " AND \"entityType\" = 'CURRICULUM_DEFAULT_REGISTRATION_RUN'"
                        + " ORDER BY \"createdAt\" DESC, \"id\" DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> new AdminAuditRow(rs.getString("id"), rs.getString("actorLabel"),
                        rs.getString("summary"), rs.getString("afterState"), instant(rs, "createdAt")));
    }

    private static String semesterSelect() {
        return "SELECT semester.\"id\", semester.\"name\", semester.\"nameEn\", semester.\"nameVi\","
                + " semester.\"type\", academic_year.\"year\" AS academic_year, semester.\"startDate\","
                + " semester.\"endDate\", semester.\"status\" FROM " + SEMESTER + " semester"
                + " JOIN " + ACADEMIC_YEAR + " academic_year ON academic_year.\"id\" = semester.\"academicYearId\"";
    }

    private static String roundSelect() {
        return "SELECT round.\"id\", round.\"semesterId\", round.\"kind\", round.\"status\","
                + " round.\"windowStart\", round.\"windowEnd\", round.\"creditLimit\" FROM " + ROUND + " round";
    }

    private static SemesterRow mapSemester(ResultSet rs, int rowNum) throws SQLException {
        return new SemesterRow(rs.getString("id"), rs.getString("name"), rs.getString("nameEn"),
                rs.getString("nameVi"), rs.getString("type"), rs.getInt("academic_year"),
                instant(rs, "startDate"), instant(rs, "endDate"), rs.getString("status"));
    }

    private static RoundRow mapRound(ResultSet rs, int rowNum) throws SQLException {
        return new RoundRow(rs.getString("id"), rs.getString("semesterId"), rs.getString("kind"),
                rs.getString("status"), instant(rs, "windowStart"), instant(rs, "windowEnd"), rs.getInt("creditLimit"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private static final class SectionBuilder {
        private final String id;
        private final String number;
        private final int capacity;
        private final int enrolled;
        private final String status;
        private final List<ScheduleRow> schedules = new ArrayList<>();

        private SectionBuilder(String id, String number, int capacity, int enrolled, String status) {
            this.id = id;
            this.number = number;
            this.capacity = capacity;
            this.enrolled = enrolled;
            this.status = status;
        }

        private SectionRow build() {
            return new SectionRow(id, number, capacity, enrolled, status, List.copyOf(schedules));
        }
    }
}
