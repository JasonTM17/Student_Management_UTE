package io.campuscore.restfulapi.people.repository;

import io.campuscore.restfulapi.people.web.PeopleReadDtos.CurriculumSummary;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.UserSummary;
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
    private static final String CURRICULUM_TABLE = "\"academic\".\"Curriculum\"";
    private static final String DEPARTMENT_TABLE = "\"academic\".\"Department\"";
    private static final RowMapper<StudentResponse> STUDENT_MAPPER = PeopleReadRepository::mapStudent;
    private static final RowMapper<LecturerResponse> LECTURER_MAPPER = PeopleReadRepository::mapLecturer;

    private final NamedParameterJdbcTemplate jdbc;

    public PeopleReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentResponse> findStudents(long offset, int limit, String status) {
        MapSqlParameterSource parameters = pageParameters(offset, limit);
        String where = studentStatusFilter(parameters, status);
        return jdbc.query(
                studentSelect()
                        + where
                        + " ORDER BY student.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                STUDENT_MAPPER);
    }

    public long countStudents(String status) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = studentStatusFilter(parameters, status);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT_TABLE + " student"
                        + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<StudentResponse> findStudentById(String id) {
        List<StudentResponse> matches = jdbc.query(
                studentSelect() + " WHERE student.\"id\" = :id",
                new MapSqlParameterSource("id", id),
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

    public List<LecturerResponse> findLecturers(long offset, int limit) {
        return jdbc.query(
                lecturerSelect()
                        + " ORDER BY lecturer.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                LECTURER_MAPPER);
    }

    public long countLecturers() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + LECTURER_TABLE,
                new MapSqlParameterSource(),
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

    private static String studentStatusFilter(MapSqlParameterSource parameters, String status) {
        if (status == null) {
            return "";
        }
        parameters.addValue("status", status);
        return " WHERE student.\"status\" = :status";
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
