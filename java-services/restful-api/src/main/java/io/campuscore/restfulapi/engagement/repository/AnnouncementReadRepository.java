package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.CourseSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.LecturerSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.PublicAnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SectionSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SemesterSummary;
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
            "id", "title", "content", "priority", "targetRoles", "targetYears",
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
     * across PostgreSQL and H2.
     */
    static final String DISPLAY_ORDER_CLAUSE =
            " ORDER BY CASE WHEN \"displayOrder\" IS NULL THEN 1 ELSE 0 END,"
                    + " \"displayOrder\", COALESCE(\"publishAt\", \"createdAt\") DESC";
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
                        + " ORDER BY \"createdAt\" DESC LIMIT :limit OFFSET :offset",
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

    /**
     * Anonymous public feed: only announcements that are editorially PUBLISHED,
     * globally visible (isGlobal — no role/year/section scoping), inside their
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
            // section, and DROPPED/CANCELLED rows do not count as enrolled —
            // the same rule AcademicMutationService uses for credit load.
            conditions.add("(\"sectionId\" IS NULL OR \"sectionId\" IN ("
                    + "SELECT enrollment.\"sectionId\" FROM academic.\"Enrollment\" enrollment"
                    + " WHERE enrollment.\"studentId\" = :viewerStudentId"
                    + " AND enrollment.\"status\" NOT IN ('DROPPED', 'CANCELLED')))");
            parameters.addValue("viewerStudentId", visibility.studentId());
        }

        if (visibility.roles().contains("LECTURER") && visibility.lecturerId() != null) {
            audience.add("\"lecturerId\" = :lecturerId");
            conditions.set(0, "(" + String.join(" OR ", audience) + ")");
            conditions.add("(\"lecturerId\" IS NULL OR \"lecturerId\" = :lecturerId)");
            parameters.addValue("lecturerId", visibility.lecturerId());
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

    public record AnnouncementFilter(String semesterId, String sectionId, String priority, String status) {
    }

    public record UserVisibility(
            List<String> roles,
            String studentId,
            Integer studentYear,
            String lecturerId,
            Instant now) {
    }

    private record SqlWhere(String sql, MapSqlParameterSource parameters) {
    }
}
