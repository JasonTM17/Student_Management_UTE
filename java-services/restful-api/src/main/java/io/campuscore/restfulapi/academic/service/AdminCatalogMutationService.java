package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small, explicit admin catalog write boundary for the course project. */
@Service
@Profile("persistence")
public class AdminCatalogMutationService {

    private static final String DEPARTMENT = "\"academic\".\"Department\"";
    private static final String ACADEMIC_YEAR = "\"academic\".\"AcademicYear\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";
    private static final String SECTION = "\"academic\".\"Section\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AdminCatalogMutationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> createDepartment(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        String facultyId = text(input, "facultyId", "faculty-demo");
        jdbc.update(
                "INSERT INTO " + DEPARTMENT
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\", \"facultyId\")"
                        + " VALUES (:id, :name, :nameEn, :nameVi, :code, :description, :facultyId)",
                params(input, id).addValue("facultyId", facultyId));
        return get(DEPARTMENT, id);
    }

    @Transactional
    public Map<String, Object> createAcademicYear(Map<String, Object> input) {
        String id = id(input);
        jdbc.update(
                "INSERT INTO " + ACADEMIC_YEAR
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\")"
                        + " VALUES (:id, :year, CAST(:startDate AS TIMESTAMPTZ),"
                        + " CAST(:endDate AS TIMESTAMPTZ), :isCurrent)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("year", number(input, "year", java.time.Year.now().getValue()))
                        .addValue("startDate", required(input, "startDate"))
                        .addValue("endDate", required(input, "endDate"))
                        .addValue("isCurrent", Boolean.parseBoolean(text(input, "isCurrent", "false"))));
        return get(ACADEMIC_YEAR, id);
    }

    @Transactional
    public Map<String, Object> createCourse(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        jdbc.update(
                "INSERT INTO " + COURSE
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"credits\", \"departmentId\")"
                        + " VALUES (:id, :code, :name, :nameEn, :nameVi, :description, :credits, :departmentId)",
                params(input, id)
                        .addValue("credits", number(input, "credits", 3))
                        .addValue("departmentId", text(input, "departmentId", "department-demo")));
        return get(COURSE, id);
    }

    @Transactional
    public Map<String, Object> createClassroom(Map<String, Object> input) {
        String id = id(input);
        jdbc.update(
                "INSERT INTO " + CLASSROOM
                        + " (\"id\", \"building\", \"roomNumber\", \"capacity\", \"type\")"
                        + " VALUES (:id, :building, :roomNumber, :capacity, :type)",
                params(input, id)
                        .addValue("capacity", number(input, "capacity", 30))
                        .addValue("type", text(input, "type", "LECTURE")));
        return get(CLASSROOM, id);
    }

    @Transactional
    public Map<String, Object> createSemester(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        jdbc.update(
                "INSERT INTO " + SEMESTER
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\","
                        + " \"startDate\", \"endDate\", \"registrationStart\", \"registrationEnd\", \"status\")"
                        + " VALUES (:id, :name, :nameEn, :nameVi, :type, :academicYearId,"
                        + " COALESCE(CAST(:startDate AS TIMESTAMPTZ), CURRENT_TIMESTAMP),"
                        + " COALESCE(CAST(:endDate AS TIMESTAMPTZ), CURRENT_TIMESTAMP + INTERVAL '5 months'),"
                        + " :registrationStart, :registrationEnd, :status)",
                params(input, id)
                        .addValue("type", text(input, "type", "FIRST"))
                        .addValue("academicYearId", text(input, "academicYearId", "academic-year-demo"))
                        .addValue("startDate", input.get("startDate"))
                        .addValue("endDate", input.get("endDate"))
                        .addValue("registrationStart", input.get("registrationStart"))
                        .addValue("registrationEnd", input.get("registrationEnd"))
                        .addValue("status", text(input, "status", "DRAFT")));
        return get(SEMESTER, id);
    }

    @Transactional
    public Map<String, Object> createSection(Map<String, Object> input) {
        String id = id(input);
        jdbc.update(
                "INSERT INTO " + SECTION
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\", \"capacity\", \"status\")"
                        + " VALUES (:id, :sectionNumber, :courseId, :semesterId, :lecturerId, :classroomId, :capacity, :status)",
                params(input, id)
                        .addValue("sectionNumber", text(input, "sectionNumber", text(input, "code", "SECTION-01")))
                        .addValue("courseId", required(input, "courseId"))
                        .addValue("semesterId", required(input, "semesterId"))
                        .addValue("lecturerId", input.get("lecturerId"))
                        .addValue("classroomId", input.get("classroomId"))
                        .addValue("capacity", number(input, "capacity", 30))
                        .addValue("status", text(input, "status", "OPEN")));
        return get(SECTION, id);
    }

    @Transactional
    public Map<String, Object> update(String table, String id, Map<String, Object> input) {
        Map<String, String> columns = allowedColumns(table);
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        boolean first = true;
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            if (!input.containsKey(entry.getKey())) {
                continue;
            }
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(entry.getValue()).append(" = ");
            if (isTimestampField(table, entry.getKey())) {
                sql.append("CAST(:").append(entry.getKey()).append(" AS TIMESTAMPTZ)");
            } else {
                sql.append(':').append(entry.getKey());
            }
            parameters.addValue(entry.getKey(), input.get(entry.getKey()));
        }
        if (first) {
            throw new IllegalArgumentException("At least one editable field is required");
        }
        sql.append(", \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id");
        int updated = jdbc.update(sql.toString(), parameters);
        if (updated == 0) {
            throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
        }
        return get(table, id);
    }

    @Transactional
    public void delete(String table, String id) {
        try {
            int deleted = jdbc.update("DELETE FROM " + table + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
            if (deleted == 0) {
                throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "RESOURCE_IN_USE", "Resource is still referenced by another record");
        }
    }

    private Map<String, Object> get(String table, String id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
    }

    private static Map<String, String> allowedColumns(String table) {
        Map<String, String> columns = new LinkedHashMap<>();
        if (ACADEMIC_YEAR.equals(table)) {
            columns.put("year", "\"year\""); columns.put("startDate", "\"startDate\"");
            columns.put("endDate", "\"endDate\""); columns.put("isCurrent", "\"isCurrent\"");
        } else if (DEPARTMENT.equals(table)) {
            columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\""); columns.put("nameVi", "\"nameVi\"");
            columns.put("code", "\"code\""); columns.put("description", "\"description\""); columns.put("facultyId", "\"facultyId\"");
        } else if (COURSE.equals(table)) {
            columns.put("code", "\"code\""); columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\"");
            columns.put("nameVi", "\"nameVi\""); columns.put("description", "\"description\""); columns.put("credits", "\"credits\"");
            columns.put("departmentId", "\"departmentId\""); columns.put("isActive", "\"isActive\"");
        } else if (CLASSROOM.equals(table)) {
            columns.put("building", "\"building\""); columns.put("roomNumber", "\"roomNumber\""); columns.put("capacity", "\"capacity\"");
            columns.put("type", "\"type\""); columns.put("isActive", "\"isActive\"");
        } else if (SEMESTER.equals(table)) {
            columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\""); columns.put("nameVi", "\"nameVi\"");
            columns.put("status", "\"status\""); columns.put("registrationStart", "\"registrationStart\"");
            columns.put("registrationEnd", "\"registrationEnd\""); columns.put("addDropStart", "\"addDropStart\"");
            columns.put("addDropEnd", "\"addDropEnd\"");
        } else if (SECTION.equals(table)) {
            columns.put("sectionNumber", "\"sectionNumber\""); columns.put("lecturerId", "\"lecturerId\"");
            columns.put("classroomId", "\"classroomId\""); columns.put("capacity", "\"capacity\""); columns.put("status", "\"status\"");
        } else {
            throw new IllegalArgumentException("Unsupported catalog resource");
        }
        return columns;
    }

    private static boolean isTimestampField(String table, String field) {
        if (ACADEMIC_YEAR.equals(table)) {
            return "startDate".equals(field) || "endDate".equals(field);
        }
        return SEMESTER.equals(table) && ("registrationStart".equals(field)
                || "registrationEnd".equals(field)
                || "addDropStart".equals(field)
                || "addDropEnd".equals(field));
    }

    private static MapSqlParameterSource params(Map<String, Object> input, String id) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", input.get("name"))
                .addValue("nameEn", input.get("nameEn"))
                .addValue("nameVi", input.get("nameVi"))
                .addValue("code", text(input, "code", id))
                .addValue("description", input.get("description"));
    }

    private static String id(Map<String, Object> input) {
        Object value = input.get("id");
        return value == null || value.toString().isBlank() ? UUID.randomUUID().toString() : value.toString().trim();
    }

    private static String required(Map<String, Object> input, String name) {
        String value = text(input, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String text(Map<String, Object> input, String name, String fallback) {
        Object value = input.get(name);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static int number(Map<String, Object> input, String name, int fallback) {
        Object value = input.get(name);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
