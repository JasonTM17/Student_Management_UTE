package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
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
 * Read adapter for the Prisma-owned academic catalog schema.
 *
 * <p>This candidate deliberately uses JDBC SELECTs only. Legacy academic
 * mutations and schema ownership remain with the Nest academic service until
 * PostgreSQL parity, canary, and rollback gates pass.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-read", name = "enabled", havingValue = "true")
public class AcademicReadRepository {

    private static final String ACADEMIC_YEAR_COLUMNS = """
            ay."id" AS ay_id, ay."year" AS ay_year, ay."startDate" AS ay_start_date,
            ay."endDate" AS ay_end_date, ay."isCurrent" AS ay_is_current,
            ay."createdAt" AS ay_created_at, ay."updatedAt" AS ay_updated_at
            """;

    private static final String DEPARTMENT_COLUMNS = """
            d."id" AS department_id, d."name" AS department_name,
            d."nameEn" AS department_name_en, d."nameVi" AS department_name_vi,
            d."code" AS department_code, d."description" AS department_description,
            d."descriptionEn" AS department_description_en,
            d."descriptionVi" AS department_description_vi,
            d."facultyId" AS department_faculty_id,
            d."isActive" AS department_is_active
            """;

    private static final RowMapper<SemesterResponse> SEMESTER_ROW_MAPPER =
            AcademicReadRepository::mapSemester;
    private static final RowMapper<CourseResponse> COURSE_ROW_MAPPER =
            AcademicReadRepository::mapCourse;

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SemesterResponse> findSemesters(long offset, int limit) {
        return jdbc.query(
                "SELECT s.\"id\", s.\"name\", s.\"nameEn\", s.\"nameVi\", s.\"type\","
                        + " s.\"academicYearId\", s.\"startDate\", s.\"endDate\","
                        + " s.\"registrationStart\", s.\"registrationEnd\","
                        + " s.\"addDropStart\", s.\"addDropEnd\", s.\"status\","
                        + " s.\"createdAt\", s.\"updatedAt\", " + ACADEMIC_YEAR_COLUMNS
                        + " FROM \"academic\".\"Semester\" s"
                        + " INNER JOIN \"academic\".\"AcademicYear\" ay"
                        + " ON ay.\"id\" = s.\"academicYearId\""
                        + " ORDER BY s.\"startDate\" DESC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                SEMESTER_ROW_MAPPER);
    }

    public long countSemesters() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Semester\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<SemesterResponse> findSemesterById(String id) {
        List<SemesterResponse> matches = jdbc.query(
                "SELECT s.\"id\", s.\"name\", s.\"nameEn\", s.\"nameVi\", s.\"type\","
                        + " s.\"academicYearId\", s.\"startDate\", s.\"endDate\","
                        + " s.\"registrationStart\", s.\"registrationEnd\","
                        + " s.\"addDropStart\", s.\"addDropEnd\", s.\"status\","
                        + " s.\"createdAt\", s.\"updatedAt\", " + ACADEMIC_YEAR_COLUMNS
                        + " FROM \"academic\".\"Semester\" s"
                        + " INNER JOIN \"academic\".\"AcademicYear\" ay"
                        + " ON ay.\"id\" = s.\"academicYearId\""
                        + " WHERE s.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                SEMESTER_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<CourseResponse> findCourses(long offset, int limit) {
        return jdbc.query(
                "SELECT c.\"id\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\","
                        + " c.\"description\", c.\"descriptionEn\", c.\"descriptionVi\","
                        + " c.\"credits\", c.\"departmentId\", c.\"semesterId\", c.\"isActive\","
                        + " c.\"createdAt\", c.\"updatedAt\", " + DEPARTMENT_COLUMNS
                        + " FROM \"academic\".\"Course\" c"
                        + " INNER JOIN \"academic\".\"Department\" d ON d.\"id\" = c.\"departmentId\""
                        + " ORDER BY c.\"code\" ASC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                COURSE_ROW_MAPPER);
    }

    public long countCourses() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Course\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<CourseResponse> findCourseById(String id) {
        List<CourseResponse> matches = jdbc.query(
                "SELECT c.\"id\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\","
                        + " c.\"description\", c.\"descriptionEn\", c.\"descriptionVi\","
                        + " c.\"credits\", c.\"departmentId\", c.\"semesterId\", c.\"isActive\","
                        + " c.\"createdAt\", c.\"updatedAt\", " + DEPARTMENT_COLUMNS
                        + " FROM \"academic\".\"Course\" c"
                        + " INNER JOIN \"academic\".\"Department\" d ON d.\"id\" = c.\"departmentId\""
                        + " WHERE c.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                COURSE_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
    }

    private static SemesterResponse mapSemester(ResultSet resultSet, int ignored)
            throws SQLException {
        AcademicYearSummary academicYear = new AcademicYearSummary(
                resultSet.getString("ay_id"),
                resultSet.getInt("ay_year"),
                instant(resultSet.getTimestamp("ay_start_date")),
                instant(resultSet.getTimestamp("ay_end_date")),
                resultSet.getBoolean("ay_is_current"),
                instant(resultSet.getTimestamp("ay_created_at")),
                instant(resultSet.getTimestamp("ay_updated_at")));
        return new SemesterResponse(
                resultSet.getString("id"),
                resultSet.getString("name"),
                resultSet.getString("nameEn"),
                resultSet.getString("nameVi"),
                resultSet.getString("type"),
                resultSet.getString("academicYearId"),
                instant(resultSet.getTimestamp("startDate")),
                instant(resultSet.getTimestamp("endDate")),
                instant(resultSet.getTimestamp("registrationStart")),
                instant(resultSet.getTimestamp("registrationEnd")),
                instant(resultSet.getTimestamp("addDropStart")),
                instant(resultSet.getTimestamp("addDropEnd")),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                academicYear);
    }

    private static CourseResponse mapCourse(ResultSet resultSet, int ignored)
            throws SQLException {
        DepartmentSummary department = new DepartmentSummary(
                resultSet.getString("department_id"),
                resultSet.getString("department_name"),
                resultSet.getString("department_name_en"),
                resultSet.getString("department_name_vi"),
                resultSet.getString("department_code"),
                resultSet.getString("department_description"),
                resultSet.getString("department_description_en"),
                resultSet.getString("department_description_vi"),
                resultSet.getString("department_faculty_id"),
                resultSet.getBoolean("department_is_active"));
        return new CourseResponse(
                resultSet.getString("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("nameEn"),
                resultSet.getString("nameVi"),
                resultSet.getString("description"),
                resultSet.getString("descriptionEn"),
                resultSet.getString("descriptionVi"),
                resultSet.getInt("credits"),
                resultSet.getString("departmentId"),
                resultSet.getString("semesterId"),
                resultSet.getBoolean("isActive"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                department);
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
