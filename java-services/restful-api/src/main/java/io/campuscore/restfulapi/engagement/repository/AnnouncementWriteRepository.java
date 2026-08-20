package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.CourseSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.LecturerSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SectionSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SemesterSummary;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Feature-gated announcement write adapter for the Java monolith.
 *
 * <p>This is a bounded source candidate only. The legacy engagement service
 * remains the public route owner, RabbitMQ publisher and rollback target until
 * PostgreSQL parity, canary and rollback gates pass.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
public class AnnouncementWriteRepository {

    private static final String TABLE = "\"engagement\".\"Announcement\"";
    private static final String SELECT_COLUMNS = """
            "id", "title", "content", "priority", "targetRoles", "targetYears",
            "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
            "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
            "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt"
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnnouncementWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnnouncementResponse create(CreateAnnouncementCommand command) {
        jdbc.getJdbcOperations().execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO "engagement"."Announcement" (
                        "id", "title", "content", "priority", "targetRoles", "targetYears",
                        "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
                        "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
                        "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt"
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, ?, NULL, ?, ?)
                    """)) {
                statement.setString(1, command.id());
                statement.setString(2, command.title());
                statement.setString(3, command.content());
                statement.setString(4, command.priority());
                statement.setArray(5, connection.createArrayOf("VARCHAR", command.targetRoles().toArray()));
                statement.setArray(6, connection.createArrayOf("INTEGER", command.targetYears().toArray()));
                statement.setBoolean(7, command.isGlobal());
                timestamp(statement, 8, command.publishAt());
                timestamp(statement, 9, command.expiresAt());
                statement.setString(10, command.publishedBy());
                statement.setString(11, command.semesterId());
                statement.setString(12, command.sectionId());
                statement.setString(13, command.lecturerId());
                timestamp(statement, 14, command.createdAt());
                timestamp(statement, 15, command.createdAt());
                statement.executeUpdate();
            }
            return null;
        });
        return findById(command.id())
                .orElseThrow(() -> new IllegalStateException("created announcement was not found"));
    }

    public Optional<AnnouncementResponse> findById(String id) {
        List<AnnouncementResponse> announcements = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                AnnouncementWriteRepository::mapRow);
        return announcements.stream().findFirst();
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(
                    index,
                    LocalDateTime.ofInstant(value, ZoneOffset.UTC),
                    Types.TIMESTAMP);
        }
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
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    public record CreateAnnouncementCommand(
            String id,
            String title,
            String content,
            String priority,
            List<String> targetRoles,
            List<Integer> targetYears,
            boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            String publishedBy,
            String semesterId,
            String sectionId,
            String lecturerId,
            Instant createdAt) {
    }
}
