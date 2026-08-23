package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomSectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumCourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentLecturerSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyDepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultySummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterCatalogSummary;
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
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC read adapter for the academic catalog. */
@Repository
@Profile("persistence")
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

    private static final String FACULTY_COLUMNS = """
            f."id" AS faculty_id, f."name" AS faculty_name,
            f."nameEn" AS faculty_name_en, f."nameVi" AS faculty_name_vi,
            f."code" AS faculty_code, f."description" AS faculty_description,
            f."descriptionEn" AS faculty_description_en,
            f."descriptionVi" AS faculty_description_vi,
            f."dean" AS faculty_dean, f."phone" AS faculty_phone,
            f."email" AS faculty_email, f."building" AS faculty_building,
            f."createdAt" AS faculty_created_at, f."updatedAt" AS faculty_updated_at,
            f."isActive" AS faculty_is_active
            """;

    private static final String DEPARTMENT_RESPONSE_COLUMNS = """
            d."id" AS department_id, d."name" AS department_name,
            d."nameEn" AS department_name_en, d."nameVi" AS department_name_vi,
            d."code" AS department_code, d."description" AS department_description,
            d."descriptionEn" AS department_description_en,
            d."descriptionVi" AS department_description_vi,
            d."chair" AS department_chair, d."phone" AS department_phone,
            d."email" AS department_email, d."building" AS department_building,
            d."facultyId" AS department_faculty_id,
            d."createdAt" AS department_created_at, d."updatedAt" AS department_updated_at,
            d."isActive" AS department_is_active
            """;

    private static final String CURRICULUM_COLUMNS = """
            cur."id" AS curriculum_id, cur."name" AS curriculum_name,
            cur."nameEn" AS curriculum_name_en, cur."nameVi" AS curriculum_name_vi,
            cur."code" AS curriculum_code, cur."departmentId" AS curriculum_department_id,
            cur."academicYearId" AS curriculum_academic_year_id,
            cur."semesterId" AS curriculum_semester_id,
            cur."totalCredits" AS curriculum_total_credits,
            cur."description" AS curriculum_description,
            cur."descriptionEn" AS curriculum_description_en,
            cur."descriptionVi" AS curriculum_description_vi,
            cur."isActive" AS curriculum_is_active,
            cur."createdAt" AS curriculum_created_at,
            cur."updatedAt" AS curriculum_updated_at
            """;

    private static final RowMapper<SemesterResponse> SEMESTER_ROW_MAPPER =
            AcademicReadRepository::mapSemester;
    private static final RowMapper<FacultyResponse> FACULTY_ROW_MAPPER =
            AcademicReadRepository::mapFaculty;
    private static final RowMapper<FacultyDepartmentSummary> FACULTY_DEPARTMENT_ROW_MAPPER =
            AcademicReadRepository::mapFacultyDepartment;
    private static final RowMapper<DepartmentResponse> DEPARTMENT_RESPONSE_ROW_MAPPER =
            AcademicReadRepository::mapDepartmentResponse;
    private static final RowMapper<DepartmentLecturerSummary> DEPARTMENT_LECTURER_ROW_MAPPER =
            AcademicReadRepository::mapDepartmentLecturer;
    private static final RowMapper<SemesterCatalogSummary> SEMESTER_CATALOG_ROW_MAPPER =
            AcademicReadRepository::mapSemesterCatalogSummary;
    private static final RowMapper<AcademicYearResponse> ACADEMIC_YEAR_ROW_MAPPER =
            AcademicReadRepository::mapAcademicYear;
    private static final RowMapper<CourseResponse> COURSE_ROW_MAPPER =
            AcademicReadRepository::mapCourse;
    private static final RowMapper<CurriculumResponse> CURRICULUM_ROW_MAPPER =
            AcademicReadRepository::mapCurriculum;
    private static final RowMapper<CurriculumCourseSummary> CURRICULUM_COURSE_ROW_MAPPER =
            AcademicReadRepository::mapCurriculumCourse;
    private static final RowMapper<ClassroomResponse> CLASSROOM_ROW_MAPPER =
            AcademicReadRepository::mapClassroom;
    private static final RowMapper<ClassroomSectionSummary> CLASSROOM_SECTION_ROW_MAPPER =
            AcademicReadRepository::mapClassroomSection;

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FacultyResponse> findFaculties(long offset, int limit) {
        return jdbc.query(
                "SELECT " + FACULTY_COLUMNS
                        + " FROM \"academic\".\"Faculty\" f"
                        + " ORDER BY f.\"name\" ASC, f.\"id\" ASC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                FACULTY_ROW_MAPPER);
    }

    public long countFaculties() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Faculty\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<FacultyResponse> findFacultyById(String id) {
        List<FacultyResponse> matches = jdbc.query(
                "SELECT " + FACULTY_COLUMNS
                        + " FROM \"academic\".\"Faculty\" f"
                        + " WHERE f.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                FACULTY_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<FacultyDepartmentSummary> findFacultyDepartmentsByFacultyIds(List<String> facultyIds) {
        if (facultyIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT " + DEPARTMENT_RESPONSE_COLUMNS
                        + " FROM \"academic\".\"Department\" d"
                        + " WHERE d.\"facultyId\" IN (:facultyIds)"
                        + " ORDER BY d.\"name\" ASC, d.\"id\" ASC",
                new MapSqlParameterSource("facultyIds", facultyIds),
                FACULTY_DEPARTMENT_ROW_MAPPER);
    }

    public List<DepartmentResponse> findDepartments(long offset, int limit) {
        return jdbc.query(
                "SELECT " + DEPARTMENT_RESPONSE_COLUMNS + ", " + FACULTY_COLUMNS
                        + " FROM \"academic\".\"Department\" d"
                        + " INNER JOIN \"academic\".\"Faculty\" f ON f.\"id\" = d.\"facultyId\""
                        + " ORDER BY d.\"name\" ASC, d.\"id\" ASC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                DEPARTMENT_RESPONSE_ROW_MAPPER);
    }

    public long countDepartments() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Department\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<DepartmentResponse> findDepartmentById(String id) {
        List<DepartmentResponse> matches = jdbc.query(
                "SELECT " + DEPARTMENT_RESPONSE_COLUMNS + ", " + FACULTY_COLUMNS
                        + " FROM \"academic\".\"Department\" d"
                        + " INNER JOIN \"academic\".\"Faculty\" f ON f.\"id\" = d.\"facultyId\""
                        + " WHERE d.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                DEPARTMENT_RESPONSE_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<DepartmentLecturerSummary> findDepartmentLecturersByDepartmentIds(List<String> departmentIds) {
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT l.\"id\", l.\"userId\", l.\"departmentId\", l.\"employeeId\","
                        + " l.\"title\", l.\"specialization\", l.\"office\", l.\"phone\","
                        + " l.\"createdAt\", l.\"updatedAt\", l.\"isActive\""
                        + " FROM \"academic\".\"Lecturer\" l"
                        + " WHERE l.\"departmentId\" IN (:departmentIds)"
                        + " ORDER BY l.\"employeeId\" ASC, l.\"id\" ASC",
                new MapSqlParameterSource("departmentIds", departmentIds),
                DEPARTMENT_LECTURER_ROW_MAPPER);
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

    public List<AcademicYearResponse> findAcademicYears(long offset, int limit) {
        return jdbc.query(
                "SELECT " + ACADEMIC_YEAR_COLUMNS
                        + " FROM \"academic\".\"AcademicYear\" ay"
                        + " ORDER BY ay.\"startDate\" DESC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                ACADEMIC_YEAR_ROW_MAPPER);
    }

    public long countAcademicYears() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"AcademicYear\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<AcademicYearResponse> findAcademicYearById(String id) {
        List<AcademicYearResponse> matches = jdbc.query(
                "SELECT " + ACADEMIC_YEAR_COLUMNS
                        + " FROM \"academic\".\"AcademicYear\" ay"
                        + " WHERE ay.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                ACADEMIC_YEAR_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<SemesterCatalogSummary> findSemesterCatalogSummariesByAcademicYearIds(List<String> academicYearIds) {
        if (academicYearIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT s.\"id\", s.\"name\", s.\"nameEn\", s.\"nameVi\", s.\"type\","
                        + " s.\"academicYearId\", s.\"startDate\", s.\"endDate\","
                        + " s.\"status\", s.\"createdAt\", s.\"updatedAt\""
                        + " FROM \"academic\".\"Semester\" s"
                        + " WHERE s.\"academicYearId\" IN (:academicYearIds)"
                        + " ORDER BY s.\"startDate\" DESC, s.\"id\" ASC",
                new MapSqlParameterSource("academicYearIds", academicYearIds),
                SEMESTER_CATALOG_ROW_MAPPER);
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

    public List<CurriculumResponse> findCurricula(long offset, int limit) {
        return jdbc.query(
                "SELECT " + CURRICULUM_COLUMNS + ", " + DEPARTMENT_RESPONSE_COLUMNS
                        + " FROM \"academic\".\"Curriculum\" cur"
                        + " INNER JOIN \"academic\".\"Department\" d ON d.\"id\" = cur.\"departmentId\""
                        + " ORDER BY cur.\"name\" ASC, cur.\"id\" ASC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                CURRICULUM_ROW_MAPPER);
    }

    public long countCurricula() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Curriculum\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<CurriculumResponse> findCurriculumById(String id) {
        List<CurriculumResponse> matches = jdbc.query(
                "SELECT " + CURRICULUM_COLUMNS + ", " + DEPARTMENT_RESPONSE_COLUMNS
                        + " FROM \"academic\".\"Curriculum\" cur"
                        + " INNER JOIN \"academic\".\"Department\" d ON d.\"id\" = cur.\"departmentId\""
                        + " WHERE cur.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                CURRICULUM_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<CurriculumCourseSummary> findCurriculumCoursesByCurriculumIds(List<String> curriculumIds) {
        if (curriculumIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT cc.\"id\" AS cc_id, cc.\"curriculumId\" AS cc_curriculum_id,"
                        + " cc.\"courseId\" AS cc_course_id, cc.\"year\" AS cc_year,"
                        + " cc.\"semester\" AS cc_semester, cc.\"isMandatory\" AS cc_is_mandatory"
                        + " FROM \"academic\".\"CurriculumCourse\" cc"
                        + " WHERE cc.\"curriculumId\" IN (:curriculumIds)",
                new MapSqlParameterSource("curriculumIds", curriculumIds),
                CURRICULUM_COURSE_ROW_MAPPER);
    }

    public List<ClassroomResponse> findClassrooms(long offset, int limit) {
        return jdbc.query(
                "SELECT cl.\"id\", cl.\"building\", cl.\"roomNumber\", cl.\"capacity\","
                        + " cl.\"type\", cl.\"isActive\", cl.\"createdAt\", cl.\"updatedAt\""
                        + " FROM \"academic\".\"Classroom\" cl"
                        + " ORDER BY cl.\"building\" ASC, cl.\"roomNumber\" ASC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                CLASSROOM_ROW_MAPPER);
    }

    public long countClassrooms() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Classroom\"",
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<ClassroomResponse> findClassroomById(String id) {
        List<ClassroomResponse> matches = jdbc.query(
                "SELECT cl.\"id\", cl.\"building\", cl.\"roomNumber\", cl.\"capacity\","
                        + " cl.\"type\", cl.\"isActive\", cl.\"createdAt\", cl.\"updatedAt\""
                        + " FROM \"academic\".\"Classroom\" cl"
                        + " WHERE cl.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                CLASSROOM_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    public List<ClassroomSectionSummary> findClassroomSectionsByClassroomIds(List<String> classroomIds) {
        if (classroomIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT section.\"id\", section.\"sectionNumber\", section.\"courseId\","
                        + " section.\"semesterId\", section.\"lecturerId\", section.\"classroomId\","
                        + " section.\"capacity\", section.\"enrolledCount\", section.\"status\""
                        + " FROM \"academic\".\"Section\" section"
                        + " WHERE section.\"classroomId\" IN (:classroomIds)"
                        + " ORDER BY section.\"sectionNumber\" ASC, section.\"id\" ASC",
                new MapSqlParameterSource("classroomIds", classroomIds),
                CLASSROOM_SECTION_ROW_MAPPER);
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
    }

    private static FacultyResponse mapFaculty(ResultSet resultSet, int ignored)
            throws SQLException {
        FacultySummary faculty = mapFacultySummary(resultSet);
        return new FacultyResponse(
                faculty.id(),
                faculty.name(),
                faculty.nameEn(),
                faculty.nameVi(),
                faculty.code(),
                faculty.description(),
                faculty.descriptionEn(),
                faculty.descriptionVi(),
                faculty.dean(),
                faculty.phone(),
                faculty.email(),
                faculty.building(),
                faculty.createdAt(),
                faculty.updatedAt(),
                faculty.isActive(),
                List.of());
    }

    private static FacultyDepartmentSummary mapFacultyDepartment(ResultSet resultSet, int ignored)
            throws SQLException {
        return new FacultyDepartmentSummary(
                resultSet.getString("department_id"),
                resultSet.getString("department_name"),
                resultSet.getString("department_name_en"),
                resultSet.getString("department_name_vi"),
                resultSet.getString("department_code"),
                resultSet.getString("department_description"),
                resultSet.getString("department_description_en"),
                resultSet.getString("department_description_vi"),
                resultSet.getString("department_chair"),
                resultSet.getString("department_phone"),
                resultSet.getString("department_email"),
                resultSet.getString("department_building"),
                resultSet.getString("department_faculty_id"),
                instant(resultSet.getTimestamp("department_created_at")),
                instant(resultSet.getTimestamp("department_updated_at")),
                resultSet.getBoolean("department_is_active"));
    }

    private static DepartmentResponse mapDepartmentResponse(ResultSet resultSet, int ignored)
            throws SQLException {
        FacultySummary faculty = mapFacultySummary(resultSet);
        return new DepartmentResponse(
                resultSet.getString("department_id"),
                resultSet.getString("department_name"),
                resultSet.getString("department_name_en"),
                resultSet.getString("department_name_vi"),
                resultSet.getString("department_code"),
                resultSet.getString("department_description"),
                resultSet.getString("department_description_en"),
                resultSet.getString("department_description_vi"),
                resultSet.getString("department_chair"),
                resultSet.getString("department_phone"),
                resultSet.getString("department_email"),
                resultSet.getString("department_building"),
                resultSet.getString("department_faculty_id"),
                instant(resultSet.getTimestamp("department_created_at")),
                instant(resultSet.getTimestamp("department_updated_at")),
                resultSet.getBoolean("department_is_active"),
                faculty,
                List.of());
    }

    private static FacultySummary mapFacultySummary(ResultSet resultSet)
            throws SQLException {
        return new FacultySummary(
                resultSet.getString("faculty_id"),
                resultSet.getString("faculty_name"),
                resultSet.getString("faculty_name_en"),
                resultSet.getString("faculty_name_vi"),
                resultSet.getString("faculty_code"),
                resultSet.getString("faculty_description"),
                resultSet.getString("faculty_description_en"),
                resultSet.getString("faculty_description_vi"),
                resultSet.getString("faculty_dean"),
                resultSet.getString("faculty_phone"),
                resultSet.getString("faculty_email"),
                resultSet.getString("faculty_building"),
                instant(resultSet.getTimestamp("faculty_created_at")),
                instant(resultSet.getTimestamp("faculty_updated_at")),
                resultSet.getBoolean("faculty_is_active"));
    }

    private static DepartmentLecturerSummary mapDepartmentLecturer(ResultSet resultSet, int ignored)
            throws SQLException {
        return new DepartmentLecturerSummary(
                resultSet.getString("id"),
                resultSet.getString("userId"),
                resultSet.getString("departmentId"),
                resultSet.getString("employeeId"),
                resultSet.getString("title"),
                resultSet.getString("specialization"),
                resultSet.getString("office"),
                resultSet.getString("phone"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                resultSet.getBoolean("isActive"));
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

    private static SemesterCatalogSummary mapSemesterCatalogSummary(ResultSet resultSet, int ignored)
            throws SQLException {
        return new SemesterCatalogSummary(
                resultSet.getString("id"),
                resultSet.getString("name"),
                resultSet.getString("nameEn"),
                resultSet.getString("nameVi"),
                resultSet.getString("type"),
                resultSet.getString("academicYearId"),
                instant(resultSet.getTimestamp("startDate")),
                instant(resultSet.getTimestamp("endDate")),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")));
    }

    private static AcademicYearResponse mapAcademicYear(ResultSet resultSet, int ignored)
            throws SQLException {
        return new AcademicYearResponse(
                resultSet.getString("ay_id"),
                resultSet.getInt("ay_year"),
                instant(resultSet.getTimestamp("ay_start_date")),
                instant(resultSet.getTimestamp("ay_end_date")),
                resultSet.getBoolean("ay_is_current"),
                instant(resultSet.getTimestamp("ay_created_at")),
                instant(resultSet.getTimestamp("ay_updated_at")),
                List.of());
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

    private static CurriculumResponse mapCurriculum(ResultSet resultSet, int ignored)
            throws SQLException {
        FacultyDepartmentSummary department = new FacultyDepartmentSummary(
                resultSet.getString("department_id"),
                resultSet.getString("department_name"),
                resultSet.getString("department_name_en"),
                resultSet.getString("department_name_vi"),
                resultSet.getString("department_code"),
                resultSet.getString("department_description"),
                resultSet.getString("department_description_en"),
                resultSet.getString("department_description_vi"),
                resultSet.getString("department_chair"),
                resultSet.getString("department_phone"),
                resultSet.getString("department_email"),
                resultSet.getString("department_building"),
                resultSet.getString("department_faculty_id"),
                instant(resultSet.getTimestamp("department_created_at")),
                instant(resultSet.getTimestamp("department_updated_at")),
                resultSet.getBoolean("department_is_active"));
        return new CurriculumResponse(
                resultSet.getString("curriculum_id"),
                resultSet.getString("curriculum_name"),
                resultSet.getString("curriculum_name_en"),
                resultSet.getString("curriculum_name_vi"),
                resultSet.getString("curriculum_code"),
                resultSet.getString("curriculum_department_id"),
                resultSet.getString("curriculum_academic_year_id"),
                resultSet.getString("curriculum_semester_id"),
                resultSet.getInt("curriculum_total_credits"),
                resultSet.getString("curriculum_description"),
                resultSet.getString("curriculum_description_en"),
                resultSet.getString("curriculum_description_vi"),
                resultSet.getBoolean("curriculum_is_active"),
                instant(resultSet.getTimestamp("curriculum_created_at")),
                instant(resultSet.getTimestamp("curriculum_updated_at")),
                department,
                List.of());
    }

    private static CurriculumCourseSummary mapCurriculumCourse(ResultSet resultSet, int ignored)
            throws SQLException {
        return new CurriculumCourseSummary(
                resultSet.getString("cc_id"),
                resultSet.getString("cc_curriculum_id"),
                resultSet.getString("cc_course_id"),
                resultSet.getInt("cc_year"),
                resultSet.getInt("cc_semester"),
                resultSet.getBoolean("cc_is_mandatory"));
    }

    private static ClassroomResponse mapClassroom(ResultSet resultSet, int ignored)
            throws SQLException {
        return new ClassroomResponse(
                resultSet.getString("id"),
                resultSet.getString("building"),
                resultSet.getString("roomNumber"),
                resultSet.getInt("capacity"),
                resultSet.getString("type"),
                resultSet.getBoolean("isActive"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                List.of());
    }

    private static ClassroomSectionSummary mapClassroomSection(ResultSet resultSet, int ignored)
            throws SQLException {
        return new ClassroomSectionSummary(
                resultSet.getString("id"),
                resultSet.getString("sectionNumber"),
                resultSet.getString("courseId"),
                resultSet.getString("semesterId"),
                resultSet.getString("lecturerId"),
                resultSet.getString("classroomId"),
                resultSet.getInt("capacity"),
                resultSet.getInt("enrolledCount"),
                resultSet.getString("status"));
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
