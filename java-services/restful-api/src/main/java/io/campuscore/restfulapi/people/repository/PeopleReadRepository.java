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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for the Prisma-owned people schema.
 *
 * <p>This candidate deliberately uses SELECTs only. People create/update/delete
 * operations, enrollment hydration, events and route ownership stay with the
 * legacy Nest people-service until data parity, canary and rollback gates pass.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.people-read", name = "enabled", havingValue = "true")
public class PeopleReadRepository {

    private static final String STUDENT_TABLE = "\"people\".\"Student\"";
    private static final String LECTURER_TABLE = "\"people\".\"Lecturer\"";
    private static final RowMapper<StudentResponse> STUDENT_MAPPER = PeopleReadRepository::mapStudent;
    private static final RowMapper<LecturerResponse> LECTURER_MAPPER = PeopleReadRepository::mapLecturer;

    private final NamedParameterJdbcTemplate jdbc;

    public PeopleReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentResponse> findStudents(long offset, int limit, String status) {
        MapSqlParameterSource parameters = pageParameters(offset, limit)
                .addValue("status", status);
        return jdbc.query(
                "SELECT \"id\", \"userId\", \"email\", \"firstName\", \"lastName\","
                        + " \"studentId\", \"curriculumId\", \"curriculumCode\", \"curriculumName\","
                        + " \"departmentId\", \"departmentCode\", \"departmentName\", \"year\","
                        + " \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\""
                        + " FROM " + STUDENT_TABLE
                        + " WHERE (:status IS NULL OR \"status\" = :status)"
                        + " ORDER BY \"createdAt\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                STUDENT_MAPPER);
    }

    public long countStudents(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT_TABLE
                        + " WHERE (:status IS NULL OR \"status\" = :status)",
                new MapSqlParameterSource("status", status),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<StudentResponse> findStudentById(String id) {
        List<StudentResponse> matches = jdbc.query(
                "SELECT \"id\", \"userId\", \"email\", \"firstName\", \"lastName\","
                        + " \"studentId\", \"curriculumId\", \"curriculumCode\", \"curriculumName\","
                        + " \"departmentId\", \"departmentCode\", \"departmentName\", \"year\","
                        + " \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\""
                        + " FROM " + STUDENT_TABLE + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                STUDENT_MAPPER);
        return matches.stream().findFirst();
    }

    public List<LecturerResponse> findLecturers(long offset, int limit) {
        return jdbc.query(
                "SELECT \"id\", \"userId\", \"email\", \"firstName\", \"lastName\","
                        + " \"departmentId\", \"departmentCode\", \"departmentName\", \"employeeId\","
                        + " \"title\", \"specialization\", \"office\", \"phone\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\""
                        + " FROM " + LECTURER_TABLE
                        + " ORDER BY \"createdAt\" DESC LIMIT :limit OFFSET :offset",
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
                "SELECT \"id\", \"userId\", \"email\", \"firstName\", \"lastName\","
                        + " \"departmentId\", \"departmentCode\", \"departmentName\", \"employeeId\","
                        + " \"title\", \"specialization\", \"office\", \"phone\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\""
                        + " FROM " + LECTURER_TABLE + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                LECTURER_MAPPER);
        return matches.stream().findFirst();
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
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
