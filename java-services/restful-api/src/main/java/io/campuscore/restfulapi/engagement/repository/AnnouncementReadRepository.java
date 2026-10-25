package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.academic.model.EnrollmentStatuses;
import io.campuscore.restfulapi.common.util.LikePattern;
import io.campuscore.restfulapi.engagement.model.AnnouncementFilter;
import io.campuscore.restfulapi.engagement.model.UserVisibility;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.CourseSummary;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.LecturerSummary;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.PublicAnnouncementResponse;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.SectionSummary;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.SemesterSummary;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC read adapter for announcements in the course database. */
@Repository
@Profile("persistence")
public class AnnouncementReadRepository {

    private static final String TABLE = "\"engagement\".\"Announcement\"";
    private static final String SELECT_COLUMNS = """
            "id", "title", "content", "priority", "targetRoles", "targetYears", "targetDepartments",
            "targetFaculties",
            "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
            "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
            "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt",
            "version", "archivedAt", "archivedBy"
            """;
    /** Safe columns for the anonymous public feed — no audience-scoping arrays. */
    private static final String PUBLIC_SELECT_COLUMNS = """
            "id", "title", "content", "summary", "coverImageUrl", "priority",
            "categoryId", "slug", "publishAt", "expiresAt", "createdAt",
            "updatedAt", "publishedBy", "lecturerDisplayName", "semesterId",
            "semesterName", "sectionId", "sectionNumber", "courseCode",
            "courseName", "lecturerId"
            """;
    /**
     * Display ordering shared by the admin list and the public feed: an
     * administrator-assigned display order wins (ascending, rows without one
     * last), then newest first. The CASE keeps NULLS LAST semantics portable
     * across PostgreSQL and H2. The trailing id keeps the rank total: seeded
     * rows share createdAt in bulk (12 identical timestamps), and an OFFSET
     * window over a tied order dropped records from full crawls and duplicated
     * ids across adjacent pages (round-3 thesis-1).
     */
    static final String DISPLAY_ORDER_CLAUSE =
            " ORDER BY CASE WHEN \"displayOrder\" IS NULL THEN 1 ELSE 0 END,"
                    + " \"displayOrder\", COALESCE(\"publishAt\", \"createdAt\") DESC, \"id\" DESC";
    /** The user feed orders by createdAt alone — the id tiebreaker is load-bearing. */
    static final String USER_ORDER_CLAUSE = " ORDER BY \"createdAt\" DESC, \"id\" DESC";
    private static final RowMapper<AnnouncementResponse> ROW_MAPPER =
            AnnouncementReadRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public AnnouncementReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AnnouncementResponse> findAll(
            AnnouncementFilter filter,
            long offset,
            int limit) {
        SqlWhere where = adminWhere(filter);
        where.parameters().addValue("offset", offset).addValue("limit", limit);
        return jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + where.sql()
                        + DISPLAY_ORDER_CLAUSE + " LIMIT :limit OFFSET :offset",
                where.parameters(),
                ROW_MAPPER);
    }

    public long countAll(AnnouncementFilter filter) {
        SqlWhere where = adminWhere(filter);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + where.sql(),
                where.parameters(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public List<AnnouncementResponse> findForUser(
            UserVisibility visibility,
            long offset,
            int limit) {
        SqlWhere where = userWhere(visibility);
        where.parameters().addValue("offset", offset).addValue("limit", limit);
        return jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + where.sql()
                        + USER_ORDER_CLAUSE + " LIMIT :limit OFFSET :offset",
                where.parameters(),
                ROW_MAPPER);
    }

    public long countForUser(UserVisibility visibility) {
        SqlWhere where = userWhere(visibility);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + where.sql(),
                where.parameters(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    /** Round-3 ct-9: single announcement fetch for the detail endpoint. */
    public java.util.Optional<AnnouncementResponse> findById(String id) {
        return jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER)
                .stream()
                .findFirst();
    }

    public java.util.Optional<AnnouncementResponse> findByIdForUser(String id, UserVisibility visibility) {
        SqlWhere where = userWhere(visibility);
        where.parameters().addValue("id", id);
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM " + TABLE + where.sql()
                        + " AND \"id\" = :id", where.parameters(), ROW_MAPPER)
                .stream().findFirst();
    }

    /**
     * Anonymous public feed: only announcements that are editorially PUBLISHED,
     * globally visible with no department targeting, inside their
     * publish window and not archived.
     */
    public List<PublicAnnouncementResponse> findPublic(long offset, int limit, Instant now) {
        MapSqlParameterSource parameters = publicWhere(now);
        parameters.addValue("offset", offset).addValue("limit", limit);
        return jdbc.query(
                "SELECT " + PUBLIC_SELECT_COLUMNS + " FROM " + TABLE + where(publicConditions(now))
                        + DISPLAY_ORDER_CLAUSE + " LIMIT :limit OFFSET :offset",
                parameters,
                PUBLIC_ROW_MAPPER);
    }

    public long countPublic(Instant now) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + where(publicConditions(now)),
                publicWhere(now),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    private static List<String> publicConditions(Instant now) {
        return List.of(
                "\"status\" = 'PUBLISHED'",
                "\"isGlobal\" = TRUE",
                "CARDINALITY(\"targetDepartments\") = 0",
                "(\"targetFaculties\" IS NULL OR CARDINALITY(\"targetFaculties\") = 0)",
                "(\"publishAt\" IS NULL OR \"publishAt\" <= :now)",
                "(\"expiresAt\" IS NULL OR \"expiresAt\" > :now)",
                "\"archivedAt\" IS NULL");
    }

    private static MapSqlParameterSource publicWhere(Instant now) {
        return new MapSqlParameterSource().addValue(
                "now",
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static final RowMapper<PublicAnnouncementResponse> PUBLIC_ROW_MAPPER =
            AnnouncementReadRepository::mapPublicRow;

    private static PublicAnnouncementResponse mapPublicRow(ResultSet resultSet, int ignored)
            throws SQLException {
        String semesterName = resultSet.getString("semesterName");
        String sectionId = resultSet.getString("sectionId");
        String sectionNumber = resultSet.getString("sectionNumber");
        String courseCode = resultSet.getString("courseCode");
        String courseName = resultSet.getString("courseName");
        String lecturerId = resultSet.getString("lecturerId");
        String lecturerDisplayName = resultSet.getString("lecturerDisplayName");

        SemesterSummary semester = present(semesterName) ? new SemesterSummary(semesterName) : null;
        CourseSummary course = present(courseCode) || present(courseName)
                ? new CourseSummary(courseCode, courseName)
                : null;
        SectionSummary section = present(sectionId)
                        || present(sectionNumber)
                        || present(courseCode)
                        || present(courseName)
                ? new SectionSummary(sectionNumber, course)
                : null;
        LecturerSummary lecturer = present(lecturerId)
                ? new LecturerSummary(lecturerId, lecturerDisplayName)
                : null;

        return new PublicAnnouncementResponse(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("summary"),
                resultSet.getString("coverImageUrl"),
                resultSet.getString("priority"),
                resultSet.getString("categoryId"),
                resultSet.getString("slug"),
                instant(resultSet, "publishAt"),
                instant(resultSet, "expiresAt"),
                instant(resultSet, "createdAt"),
                instant(resultSet, "updatedAt"),
                resultSet.getString("publishedBy"),
                lecturerDisplayName,
                semesterName,
                semester,
                section,
                lecturer);
    }

    private static SqlWhere adminWhere(AnnouncementFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.semesterId() != null) {
            conditions.add("\"semesterId\" = :semesterId");
            parameters.addValue("semesterId", filter.semesterId());
        }
        if (filter.sectionId() != null) {
            conditions.add("\"sectionId\" = :sectionId");
            parameters.addValue("sectionId", filter.sectionId());
        }
        if (filter.priority() != null) {
            conditions.add("\"priority\" = :priority");
            parameters.addValue("priority", filter.priority());
        }
        if ("ACTIVE".equals(filter.status())) {
            conditions.add("\"archivedAt\" IS NULL");
        } else if ("ARCHIVED".equals(filter.status())) {
            conditions.add("\"archivedAt\" IS NOT NULL");
        }
        List<String> tokens = LikePattern.tokens(filter.search());
        if (!tokens.isEmpty()) {
            parameters.addValue("foldFrom", LikePattern.FOLD_FROM);
            parameters.addValue("foldTo", LikePattern.FOLD_TO);
            for (int i = 0; i < tokens.size(); i++) {
                String paramName = "searchToken" + i;
                parameters.addValue(paramName, tokens.get(i));
                conditions.add("(TRANSLATE(LOWER(COALESCE(\"title\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"content\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"slug\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"courseCode\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"courseName\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"semesterName\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(COALESCE(\"lecturerDisplayName\", '')), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\')");
            }
        }
        return new SqlWhere(where(conditions), parameters);
    }

    private static SqlWhere userWhere(UserVisibility visibility) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(
                        "now",
                        OffsetDateTime.ofInstant(visibility.now(), ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE);

        List<String> audience = new ArrayList<>();
        audience.add("\"isGlobal\" = TRUE");
        for (int index = 0; index < visibility.roles().size(); index++) {
            String parameter = "role" + index;
            audience.add(":" + parameter + " = ANY(\"targetRoles\")");
            parameters.addValue(parameter, visibility.roles().get(index));
        }
        conditions.add("(" + String.join(" OR ", audience) + ")");
        conditions.add("(\"publishAt\" IS NULL OR \"publishAt\" <= :now)");
        conditions.add("(\"expiresAt\" IS NULL OR \"expiresAt\" > :now)");
        conditions.add("\"archivedAt\" IS NULL");

        if (visibility.studentId() != null) {
            if (visibility.studentYear() == null) {
                conditions.add("CARDINALITY(\"targetYears\") = 0");
            } else {
                conditions.add("(CARDINALITY(\"targetYears\") = 0"
                        + " OR :studentYear = ANY(\"targetYears\"))");
                parameters.addValue("studentYear", visibility.studentYear());
            }
            // A notice scoped to a teaching section must only reach students
            // currently enrolled in it. The admin editor advertises this
            // restriction, but the reader previously ignored "sectionId"
            // entirely, so every section-scoped notice was visible to the whole
            // role/year cohort. The predicate only narrows notices that name a
            // section, and only the shared active-enrollment statuses count as
            // enrolled — WITHDRAWN released the seat, same as the credit-load
            // rule AcademicMutationService uses.
            conditions.add("(\"sectionId\" IS NULL OR \"sectionId\" IN ("
                    + "SELECT enrollment.\"sectionId\" FROM academic.\"Enrollment\" enrollment"
                    + " WHERE enrollment.\"studentId\" = :viewerStudentId"
                    + " AND enrollment.\"status\" IN "
                    + EnrollmentStatuses.ACTIVE_STATUS_PREDICATE + "))");
            parameters.addValue("viewerStudentId", visibility.studentId());
        }

        if (visibility.roles().contains("LECTURER") && visibility.lecturerId() != null) {
            audience.add("\"lecturerId\" = :lecturerId");
            conditions.set(0, "(" + String.join(" OR ", audience) + ")");
            conditions.add("(\"lecturerId\" IS NULL OR \"lecturerId\" = :lecturerId)");
            parameters.addValue("lecturerId", visibility.lecturerId());
        }

        // Department targeting (ATT1): AND across dimensions. A student's
        // department is the one on their curriculum (Student → Curriculum →
        // Department); a lecturer's is Lecturer.departmentId. An empty
        // targetDepartments matches every department exactly like an empty
        // targetYears matches every year. A dual-role viewer matches when
        // either identity resolves a listed department; a viewer with no
        // resolvable department only sees untargeted notices — NULL = ANY
        // never passes.
        java.util.List<String> departmentMatches = new java.util.ArrayList<>();
        if (visibility.studentId() != null) {
            departmentMatches.add("EXISTS ("
                    + "SELECT 1 FROM academic.\"Student\" viewer_student"
                    + " JOIN academic.\"Curriculum\" viewer_curriculum"
                    + " ON viewer_curriculum.\"id\" = viewer_student.\"curriculumId\""
                    + " WHERE viewer_student.\"id\" = :viewerStudentId"
                    + " AND viewer_curriculum.\"departmentId\" = ANY(\"targetDepartments\"))");
        }
        if (visibility.roles().contains("LECTURER") && visibility.lecturerId() != null) {
            departmentMatches.add("EXISTS ("
                    + "SELECT 1 FROM academic.\"Lecturer\" viewer_lecturer"
                    + " WHERE viewer_lecturer.\"id\" = :lecturerId"
                    + " AND viewer_lecturer.\"departmentId\" = ANY(\"targetDepartments\"))");
        }
        if (departmentMatches.isEmpty()) {
            conditions.add("CARDINALITY(\"targetDepartments\") = 0");
        } else {
            conditions.add("(CARDINALITY(\"targetDepartments\") = 0 OR "
                    + String.join(" OR ", departmentMatches) + ")");
        }

        // Faculty targeting (R14, V149): AND across dimensions. A student's
        // faculty is the one their curriculum's department belongs to
        // (Student → Curriculum → Department → Faculty); a lecturer's is the
        // faculty of Lecturer.departmentId. An empty targetFaculties matches
        // every faculty exactly like an empty targetYears matches every year
        // (NULL is treated as empty — the production column is NOT NULL
        // DEFAULT ARRAY[] but fixture rows may still carry NULL). A dual-role
        // viewer matches when either identity resolves a listed faculty; a
        // viewer with no resolvable faculty only sees untargeted notices —
        // NULL = ANY never passes.
        java.util.List<String> facultyMatches = new java.util.ArrayList<>();
        if (visibility.studentId() != null) {
            facultyMatches.add("EXISTS ("
                    + "SELECT 1 FROM academic.\"Student\" faculty_student"
                    + " JOIN academic.\"Curriculum\" faculty_curriculum"
                    + " ON faculty_curriculum.\"id\" = faculty_student.\"curriculumId\""
                    + " JOIN academic.\"Department\" faculty_department"
                    + " ON faculty_department.\"id\" = faculty_curriculum.\"departmentId\""
                    + " WHERE faculty_student.\"id\" = :viewerStudentId"
                    + " AND faculty_department.\"facultyId\" = ANY(\"targetFaculties\"))");
        }
        if (visibility.roles().contains("LECTURER") && visibility.lecturerId() != null) {
            facultyMatches.add("EXISTS ("
                    + "SELECT 1 FROM academic.\"Lecturer\" faculty_lecturer"
                    + " JOIN academic.\"Department\" faculty_lecturer_department"
                    + " ON faculty_lecturer_department.\"id\" = faculty_lecturer.\"departmentId\""
                    + " WHERE faculty_lecturer.\"id\" = :lecturerId"
                    + " AND faculty_lecturer_department.\"facultyId\" = ANY(\"targetFaculties\"))");
        }
        if (facultyMatches.isEmpty()) {
            conditions.add("(\"targetFaculties\" IS NULL OR CARDINALITY(\"targetFaculties\") = 0)");
        } else {
            conditions.add("(\"targetFaculties\" IS NULL OR CARDINALITY(\"targetFaculties\") = 0 OR "
                    + String.join(" OR ", facultyMatches) + ")");
        }
        return new SqlWhere(where(conditions), parameters);
    }

    private static String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static AnnouncementResponse mapRow(ResultSet resultSet, int ignored)
            throws SQLException {
        String semesterName = resultSet.getString("semesterName");
        String sectionId = resultSet.getString("sectionId");
        String sectionNumber = resultSet.getString("sectionNumber");
        String courseCode = resultSet.getString("courseCode");
        String courseName = resultSet.getString("courseName");
        String lecturerId = resultSet.getString("lecturerId");
        String lecturerDisplayName = resultSet.getString("lecturerDisplayName");

        SemesterSummary semester = present(semesterName) ? new SemesterSummary(semesterName) : null;
        CourseSummary course = present(courseCode) || present(courseName)
                ? new CourseSummary(courseCode, courseName)
                : null;
        SectionSummary section = present(sectionId)
                        || present(sectionNumber)
                        || present(courseCode)
                        || present(courseName)
                ? new SectionSummary(sectionNumber, course)
                : null;
        LecturerSummary lecturer = present(lecturerId)
                ? new LecturerSummary(lecturerId, lecturerDisplayName)
                : null;

        return new AnnouncementResponse(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("priority"),
                stringList(resultSet.getArray("targetRoles")),
                integerList(resultSet.getArray("targetYears")),
                stringList(resultSet.getArray("targetDepartments")),
                stringList(resultSet.getArray("targetFaculties")),
                resultSet.getBoolean("isGlobal"),
                instant(resultSet, "publishAt"),
                instant(resultSet, "expiresAt"),
                resultSet.getString("publishedBy"),
                resultSet.getString("semesterId"),
                semesterName,
                sectionId,
                sectionNumber,
                courseCode,
                courseName,
                lecturerId,
                lecturerDisplayName,
                instant(resultSet, "createdAt"),
                instant(resultSet, "updatedAt"),
                resultSet.getInt("version"),
                instant(resultSet, "archivedAt"),
                resultSet.getString("archivedBy"),
                semester,
                section,
                lecturer);
    }

    private static List<String> stringList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] values = (Object[]) sqlArray.getArray();
        List<String> result = new ArrayList<>(values.length);
        for (Object value : values) {
            result.add(value.toString());
        }
        return List.copyOf(result);
    }

    private static List<Integer> integerList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] values = (Object[]) sqlArray.getArray();
        List<Integer> result = new ArrayList<>(values.length);
        for (Object value : values) {
            result.add(((Number) value).intValue());
        }
        return List.copyOf(result);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private record SqlWhere(String sql, MapSqlParameterSource parameters) {
    }
}
