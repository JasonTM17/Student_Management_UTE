package io.campuscore.restfulapi.people.repository;

import io.campuscore.restfulapi.academic.model.EnrollmentStatuses;
import io.campuscore.restfulapi.common.util.LikePattern;
import io.campuscore.restfulapi.people.dto.PeopleReadDtos.CurriculumSummary;
import io.campuscore.restfulapi.people.dto.PeopleReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.people.dto.PeopleReadDtos.LecturerResponse;
import io.campuscore.restfulapi.people.dto.PeopleReadDtos.StudentResponse;
import io.campuscore.restfulapi.people.dto.PeopleReadDtos.UserSummary;
import io.campuscore.restfulapi.people.model.LecturerDirectoryScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read adapter for student and lecturer records in the course database. */
@Repository
@Profile("persistence")
public class PeopleReadRepository {

    private static final String STUDENT_TABLE = "\"academic\".\"Student\"";
    private static final String LECTURER_TABLE = "\"academic\".\"Lecturer\"";
    private static final String USER_TABLE = "\"campuscore_auth\".\"User\"";
    private static final String AUTH_LECTURER_TABLE = "\"campuscore_auth\".\"Lecturer\"";
    private static final String CURRICULUM_TABLE = "\"academic\".\"Curriculum\"";
    private static final String DEPARTMENT_TABLE = "\"academic\".\"Department\"";
    private static final String ENROLLMENT_TABLE = "\"academic\".\"Enrollment\"";
    private static final String SECTION_TABLE = "\"academic\".\"Section\"";
    private static final String COURSE_TABLE = "\"academic\".\"Course\"";
    // The only status PeopleReadService.STUDENT_STATUSES admits; kept in sync
    // by that service's validation and referenced here for the fan-out roster.
    private static final String ACTIVE_STUDENT_STATUS = "ACTIVE";
    private static final RowMapper<StudentResponse> STUDENT_MAPPER = PeopleReadRepository::mapStudent;
    private static final RowMapper<LecturerResponse> LECTURER_MAPPER = PeopleReadRepository::mapLecturer;

    private final NamedParameterJdbcTemplate jdbc;

    public PeopleReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentResponse> findStudents(long offset, int limit, String status, LecturerDirectoryScope scope) {
        MapSqlParameterSource parameters = pageParameters(offset, limit);
        String where = studentWhere(parameters, status, scope);
        return jdbc.query(
                studentSelect()
                        + where
                        + " ORDER BY student.\"createdAt\" DESC, student.\"id\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                STUDENT_MAPPER);
    }

    public long countStudents(String status, LecturerDirectoryScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = studentWhere(parameters, status, scope);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT_TABLE + " student"
                        + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    /**
     * Auth-account ids of every ACTIVE student, in stable order. This is the
     * fan-out roster for campus-wide notices (R7 announcement notifications).
     * The join mirrors {@link #studentSelect()}'s user join: the notification's
     * {@code user_id} carries a foreign key to {@code campuscore_auth."User"},
     * so a student row without a matching account must not reach the batch.
     * {@code DISTINCT} collapses any accidental duplicate account link.
     */
    public List<String> findActiveStudentUserIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT student.\"userId\" FROM " + STUDENT_TABLE + " student"
                        + " JOIN " + USER_TABLE + " user_account ON user_account.\"id\" = student.\"userId\""
                        + " WHERE student.\"status\" = :status"
                        + " ORDER BY 1",
                new MapSqlParameterSource("status", ACTIVE_STUDENT_STATUS),
                String.class);
    }
    /** Active students whose academic year is one of the given years (empty/NULL years = all). */
    public List<String> findActiveStudentUserIdsInYears(List<Integer> years) {
        if (years == null || years.isEmpty()) {
            return findActiveStudentUserIds();
        }
        return jdbc.queryForList(
                "SELECT DISTINCT student.\"userId\" FROM " + STUDENT_TABLE + " student"
                        + " JOIN " + USER_TABLE + " user_account ON user_account.\"id\" = student.\"userId\""
                        + " WHERE student.\"status\" = :status AND student.\"year\" IN (:years)"
                        + " ORDER BY 1",
                new MapSqlParameterSource("status", ACTIVE_STUDENT_STATUS)
                        .addValue("years", years),
                String.class);
    }

    /** Eligible inbox recipients: the same year/active-enrollment scope as the notice reader. */
    public List<String> findAnnouncementStudentUserIds(List<Integer> years, String sectionId,
            List<String> targetRoles, String lecturerId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("status", ACTIVE_STUDENT_STATUS);
        String filter = "";
        if (targetRoles != null && !targetRoles.isEmpty()) {
            // Academic profiles survive role revocation. An inbox must not
            // disclose a role-scoped notice that the current feed would hide.
            filter += " AND EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                    + " JOIN campuscore_auth.\"Role\" role ON role.\"id\" = ur.\"roleId\""
                    + " WHERE ur.\"userId\" = student.\"userId\" AND role.\"name\" IN (:targetRoles))";
            parameters.addValue("targetRoles", targetRoles);
        }
        if (lecturerId != null) {
            // The feed also restricts dual-role lecturers to notices assigned
            // to their lecturer identity; student-only recipients are unaffected.
            filter += " AND (NOT EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                    + " JOIN campuscore_auth.\"Role\" role ON role.\"id\" = ur.\"roleId\""
                    + " WHERE ur.\"userId\" = student.\"userId\" AND role.\"name\" = 'LECTURER')"
                    + " OR EXISTS (SELECT 1 FROM campuscore_auth.\"Lecturer\" lecturer"
                    + " WHERE lecturer.\"userId\" = student.\"userId\" AND lecturer.\"id\" = :noticeLecturerId))";
            parameters.addValue("noticeLecturerId", lecturerId);
        }
        if (years != null && !years.isEmpty()) {
            filter += " AND student.\"year\" IN (:years)";
            parameters.addValue("years", years);
        }
        if (sectionId != null) {
            filter += " AND EXISTS (SELECT 1 FROM academic.\"Enrollment\" enrollment"
                    + " WHERE enrollment.\"studentId\" = student.\"id\""
                    + " AND enrollment.\"sectionId\" = :sectionId"
                    + " AND enrollment.\"status\" IN "
                    + EnrollmentStatuses.ACTIVE_STATUS_PREDICATE + ")";
            parameters.addValue("sectionId", sectionId);
        }
        return jdbc.queryForList("SELECT DISTINCT student.\"userId\" FROM " + STUDENT_TABLE + " student"
                        + " JOIN " + USER_TABLE + " user_account ON user_account.\"id\" = student.\"userId\""
                        + " WHERE student.\"status\" = :status AND user_account.\"status\" = :status"
                        + filter + " ORDER BY 1", parameters, String.class);
    }

    public Optional<StudentResponse> findStudentById(String id, LecturerDirectoryScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        List<StudentResponse> matches = jdbc.query(
                studentSelect() + " WHERE student.\"id\" = :id" + studentScopePredicate(parameters, scope, true),
                parameters,
                STUDENT_MAPPER);
        return matches.stream().findFirst();
    }

    public Optional<StudentResponse> findStudentByIdAndUserId(String id, String userId) {
        List<StudentResponse> matches = jdbc.query(
                studentSelect() + " WHERE student.\"id\" = :id AND student.\"userId\" = :userId",
                new MapSqlParameterSource().addValue("id", id).addValue("userId", userId),
                STUDENT_MAPPER);
        return matches.stream().findFirst();
    }

    public List<LecturerResponse> findLecturers(long offset, int limit, String search) {
        MapSqlParameterSource parameters = pageParameters(offset, limit);
        return jdbc.query(
                lecturerSelect()
                        + searchWhere(parameters, search)
                        + " ORDER BY lecturer.\"createdAt\" DESC, lecturer.\"id\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                LECTURER_MAPPER);
    }

    public long countLecturers(String search) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + LECTURER_TABLE + " lecturer"
                        + searchWhere(parameters, search),
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<LecturerResponse> findLecturerById(String id) {
        List<LecturerResponse> matches = jdbc.query(
                lecturerSelect() + " WHERE lecturer.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                LECTURER_MAPPER);
        return matches.stream().findFirst();
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
    }

    /**
     * Optional lecturer search predicate. The name and email live on the auth
     * account, so they are reached through EXISTS instead of adding a join to the
     * {@code COUNT(*)} statement - the list and the total then run the same
     * predicate and a request without {@code search} keeps its original SQL.
     *
     * <p>{@code search} arrives already lowered, escaped, and diacritic-folded by
     * {@link LikePattern#contains} at the service layer; this predicate only adds
     * the column-side {@code TRANSLATE} fold so "nguyen" matches "Nguyễn".
     * Applying {@code contains} here as well would wrap the pattern twice.
     */
    private static String searchWhere(MapSqlParameterSource parameters, String search) {
        if (search == null) {
            return "";
        }
        List<String> tokens = LikePattern.tokensFromContainsPattern(search);
        if (tokens.isEmpty()) {
            return "";
        }
        parameters.addValue("foldFrom", LikePattern.FOLD_FROM);
        parameters.addValue("foldTo", LikePattern.FOLD_TO);
        StringBuilder where = new StringBuilder(" WHERE ");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            String paramName = "searchToken" + i;
            parameters.addValue(paramName, tokens.get(i));
            where.append("(TRANSLATE(LOWER(lecturer.\"employeeId\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(COALESCE(lecturer.\"title\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(COALESCE(lecturer.\"specialization\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR EXISTS (SELECT 1 FROM ").append(USER_TABLE).append(" su")
                    .append(" WHERE su.\"id\" = lecturer.\"userId\"")
                    .append(" AND (TRANSLATE(LOWER(su.\"firstName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(su.\"lastName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(su.\"email\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'))")
                    .append(" OR EXISTS (SELECT 1 FROM ").append(DEPARTMENT_TABLE).append(" sd")
                    .append(" WHERE sd.\"id\" = lecturer.\"departmentId\"")
                    .append(" AND (TRANSLATE(LOWER(sd.\"name\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(sd.\"code\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\')))");
        }
        return where.toString();
    }

    private static String studentStatusFilter(MapSqlParameterSource parameters, String status) {
        if (status == null) {
            return "";
        }
        parameters.addValue("status", status);
        return " WHERE student.\"status\" = :status";
    }

    private static String studentWhere(MapSqlParameterSource parameters, String status, LecturerDirectoryScope scope) {
        StringBuilder where = new StringBuilder();
        if (status != null) {
            parameters.addValue("status", status);
            where.append("student.\"status\" = :status");
        }
        String scopePredicate = studentScopePredicate(parameters, scope, false);
        if (!scopePredicate.isEmpty()) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append(scopePredicate);
        }
        return where.isEmpty() ? "" : " WHERE " + where;
    }

    private static String studentScopePredicate(
            MapSqlParameterSource parameters, LecturerDirectoryScope scope, boolean andPrefixed) {
        if (scope == null) {
            return "";
        }
        parameters.addValue("scopeLecturerId", scope.lecturerId())
                .addValue("scopeUserId", scope.userId())
                .addValue("scopeDepartment", scope.departmentScoped())
                .addValue("scopeFaculty", scope.facultyScoped());
        String predicate = "(student.\"userId\" = :scopeUserId"
                + " OR EXISTS (SELECT 1 FROM " + ENROLLMENT_TABLE + " enrollment"
                + " JOIN " + SECTION_TABLE + " section ON section.\"id\" = enrollment.\"sectionId\""
                + " JOIN " + COURSE_TABLE + " course ON course.\"id\" = section.\"courseId\""
                + " WHERE enrollment.\"studentId\" = student.\"id\""
                + " AND (section.\"lecturerId\" = :scopeLecturerId"
                + " OR EXISTS (SELECT 1 FROM " + LECTURER_TABLE + " lecturer"
                + " LEFT JOIN " + DEPARTMENT_TABLE + " lecturer_dept ON lecturer_dept.\"id\" = lecturer.\"departmentId\""
                + " LEFT JOIN " + DEPARTMENT_TABLE + " course_dept ON course_dept.\"id\" = course.\"departmentId\""
                + " WHERE lecturer.\"id\" = :scopeLecturerId"
                + " AND ((:scopeDepartment AND course.\"departmentId\" = lecturer.\"departmentId\")"
                + " OR (:scopeFaculty AND course_dept.\"facultyId\" = lecturer_dept.\"facultyId\")))))"
                + " OR EXISTS (SELECT 1 FROM thesis.thesis_group_member member"
                + " JOIN thesis.thesis_group grp ON grp.id = member.group_id"
                + " JOIN " + AUTH_LECTURER_TABLE + " scope_lecturer"
                + " ON (scope_lecturer.\"id\" = :scopeLecturerId OR scope_lecturer.\"userId\" = :scopeUserId)"
                + " AND scope_lecturer.\"isActive\" = TRUE"
                + " WHERE member.student_id = student.\"id\""
                + " AND (EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor supervisor"
                + " WHERE supervisor.topic_id = grp.topic_id"
                + " AND (supervisor.lecturer_id = scope_lecturer.\"id\" OR supervisor.lecturer_id = scope_lecturer.\"userId\"))"
                + " OR EXISTS (SELECT 1 FROM thesis.thesis_topic topic"
                + " WHERE topic.id = grp.topic_id AND topic.gvpb_lecturer_id = scope_lecturer.\"id\")"
                + " OR EXISTS (SELECT 1 FROM thesis.thesis_council_topic council_topic"
                + " JOIN thesis.thesis_council_member council_member ON council_member.council_id = council_topic.council_id"
                + " WHERE council_topic.topic_id = grp.topic_id"
                + " AND (council_member.lecturer_id = scope_lecturer.\"id\""
                + " OR council_member.lecturer_id = scope_lecturer.\"userId\")))))";
        return andPrefixed ? " AND " + predicate : predicate;
    }

    private static String studentSelect() {
        return "SELECT student.\"id\", student.\"userId\", user_account.\"email\","
                + " user_account.\"firstName\", user_account.\"lastName\", student.\"studentId\","
                + " student.\"curriculumId\", curriculum.\"code\" AS \"curriculumCode\","
                + " curriculum.\"name\" AS \"curriculumName\", department.\"id\" AS \"departmentId\","
                + " department.\"code\" AS \"departmentCode\", department.\"name\" AS \"departmentName\","
                + " student.\"year\", student.\"status\", student.\"admissionDate\","
                + " student.\"createdAt\", student.\"updatedAt\""
                + " FROM " + STUDENT_TABLE + " student"
                + " JOIN " + USER_TABLE + " user_account ON user_account.\"id\" = student.\"userId\""
                + " JOIN " + CURRICULUM_TABLE + " curriculum ON curriculum.\"id\" = student.\"curriculumId\""
                + " JOIN " + DEPARTMENT_TABLE + " department ON department.\"id\" = curriculum.\"departmentId\"";
    }

    private static String lecturerSelect() {
        return "SELECT lecturer.\"id\", lecturer.\"userId\", user_account.\"email\","
                + " user_account.\"firstName\", user_account.\"lastName\", lecturer.\"departmentId\","
                + " department.\"code\" AS \"departmentCode\", department.\"name\" AS \"departmentName\","
                + " lecturer.\"employeeId\", lecturer.\"title\", lecturer.\"specialization\","
                + " lecturer.\"office\", lecturer.\"phone\", lecturer.\"isActive\","
                + " lecturer.\"createdAt\", lecturer.\"updatedAt\""
                + " FROM " + LECTURER_TABLE + " lecturer"
                + " JOIN " + USER_TABLE + " user_account ON user_account.\"id\" = lecturer.\"userId\""
                + " JOIN " + DEPARTMENT_TABLE + " department ON department.\"id\" = lecturer.\"departmentId\"";
    }

    private static StudentResponse mapStudent(ResultSet resultSet, int ignored)
            throws SQLException {
        DepartmentSummary department = resultSet.getString("departmentId") == null
                ? null
                : new DepartmentSummary(
                        resultSet.getString("departmentId"),
                        resultSet.getString("departmentCode"),
                        resultSet.getString("departmentName"));
        return new StudentResponse(
                resultSet.getString("id"),
                resultSet.getString("userId"),
                resultSet.getString("studentId"),
                resultSet.getString("curriculumId"),
                resultSet.getInt("year"),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("admissionDate")),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                user(resultSet),
                new CurriculumSummary(
                        resultSet.getString("curriculumId"),
                        resultSet.getString("curriculumCode"),
                        resultSet.getString("curriculumName"),
                        department));
    }

    private static LecturerResponse mapLecturer(ResultSet resultSet, int ignored)
            throws SQLException {
        return new LecturerResponse(
                resultSet.getString("id"),
                resultSet.getString("userId"),
                resultSet.getString("departmentId"),
                resultSet.getString("employeeId"),
                resultSet.getString("title"),
                resultSet.getString("specialization"),
                resultSet.getString("office"),
                resultSet.getString("phone"),
                resultSet.getBoolean("isActive"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                user(resultSet),
                new DepartmentSummary(
                        resultSet.getString("departmentId"),
                        resultSet.getString("departmentCode"),
                        resultSet.getString("departmentName")));
    }

    private static UserSummary user(ResultSet resultSet) throws SQLException {
        return new UserSummary(
                resultSet.getString("userId"),
                resultSet.getString("email"),
                resultSet.getString("firstName"),
                resultSet.getString("lastName"));
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
