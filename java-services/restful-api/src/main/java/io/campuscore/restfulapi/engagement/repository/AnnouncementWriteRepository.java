package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.CourseSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.LecturerSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SectionSummary;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.SemesterSummary;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL write adapter for announcements owned by the Java API. */
@Repository
@Profile("persistence")
public class AnnouncementWriteRepository {

    private static final String TABLE = "\"engagement\".\"Announcement\"";
    private static final String SELECT_COLUMNS = """
            "id", "title", "content", "priority", "targetRoles", "targetYears",
            "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
            "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
            "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt",
            "version", "archivedAt", "archivedBy"
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

    public int update(UpdateAnnouncementCommand command) {
        List<String> assignments = new ArrayList<>();
        List<SqlBinder> binders = new ArrayList<>();
        addString(assignments, binders, "title", command.title());
        addString(assignments, binders, "content", command.content());
        addString(assignments, binders, "priority", command.priority());
        addStringArray(assignments, binders, "targetRoles", command.targetRoles());
        addIntegerArray(assignments, binders, "targetYears", command.targetYears());
        addBoolean(assignments, binders, "isGlobal", command.isGlobal());
        addInstant(assignments, binders, "publishAt", command.publishAt());
        addInstant(assignments, binders, "expiresAt", command.expiresAt());
        addString(assignments, binders, "semesterId", command.semesterId());
        clearString(assignments, binders, command.semesterId(), "semesterName");
        addString(assignments, binders, "sectionId", command.sectionId());
        clearString(assignments, binders, command.sectionId(), "sectionNumber");
        clearString(assignments, binders, command.sectionId(), "courseCode");
        clearString(assignments, binders, command.sectionId(), "courseName");
        addString(assignments, binders, "lecturerId", command.lecturerId());
        clearString(assignments, binders, command.lecturerId(), "lecturerDisplayName");
        assignments.add("\"version\" = \"version\" + 1");
        assignments.add("\"updatedAt\" = ?");
        binders.add((statement, index, ignored) -> timestamp(statement, index, command.updatedAt()));

        Integer changed = jdbc.getJdbcOperations().execute((ConnectionCallback<Integer>) connection -> {
            String sql = "UPDATE \"engagement\".\"Announcement\" SET "
                    + String.join(", ", assignments)
                    + " WHERE \"id\" = ? AND \"version\" = ? AND \"archivedAt\" IS NULL";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (SqlBinder binder : binders) {
                    binder.bind(statement, index++, connection);
                }
                statement.setString(index, command.id());
                statement.setInt(index + 1, command.expectedVersion());
                return statement.executeUpdate();
            }
        });
        return changed == null ? 0 : changed;
    }

    public int archive(TransitionCommand command) {
        return transition(command, true);
    }

    public int restore(TransitionCommand command) {
        return transition(command, false);
    }

    private int transition(TransitionCommand command, boolean archive) {
        String sql = archive
                ? "UPDATE " + TABLE + " SET \"archivedAt\" = ?, \"archivedBy\" = ?, "
                        + "\"version\" = \"version\" + 1, \"updatedAt\" = ? "
                        + "WHERE \"id\" = ? AND \"version\" = ? AND \"archivedAt\" IS NULL"
                : "UPDATE " + TABLE + " SET \"archivedAt\" = NULL, \"archivedBy\" = NULL, "
                        + "\"version\" = \"version\" + 1, \"updatedAt\" = ? "
                        + "WHERE \"id\" = ? AND \"version\" = ? AND \"archivedAt\" IS NOT NULL";
        Integer changed = jdbc.getJdbcOperations().execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (archive) {
                    timestamp(statement, index++, command.archivedAt());
                    statement.setString(index++, command.archivedBy());
                }
                timestamp(statement, index++, command.updatedAt());
                statement.setString(index++, command.id());
                statement.setInt(index, command.expectedVersion());
                return statement.executeUpdate();
            }
        });
        return changed == null ? 0 : changed;
    }

    public Optional<AnnouncementResponse> findById(String id) {
        return findById(id, false);
    }

    /**
     * Loads an announcement while holding its row lock for the surrounding transaction.
     * Mutation services use this read before the CAS write so the audit snapshots cannot
     * be interleaved with another writer on the same announcement.
     */
    public Optional<AnnouncementResponse> findByIdForUpdate(String id) {
        return findById(id, true);
    }

    private Optional<AnnouncementResponse> findById(String id, boolean forUpdate) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        List<AnnouncementResponse> announcements = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + " WHERE \"id\" = :id" + lockClause,
                new MapSqlParameterSource("id", id),
                AnnouncementWriteRepository::mapRow);
        return announcements.stream().findFirst();
    }

    private static void addString(
            List<String> assignments,
            List<SqlBinder> binders,
            String column,
            PatchValue<String> value) {
        if (value.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, ignored) -> {
                if (value.value() == null) {
                    statement.setNull(index, Types.VARCHAR);
                } else {
                    statement.setString(index, value.value());
                }
            });
        }
    }

    private static void clearString(
            List<String> assignments,
            List<SqlBinder> binders,
            PatchValue<String> ownerValue,
            String column) {
        if (ownerValue.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, ignored) -> statement.setNull(index, Types.VARCHAR));
        }
    }

    private static void addStringArray(
            List<String> assignments,
            List<SqlBinder> binders,
            String column,
            PatchValue<List<String>> values) {
        if (values.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, connection) -> {
                if (values.value() == null) {
                    statement.setNull(index, Types.ARRAY);
                } else {
                    statement.setArray(index, connection.createArrayOf("VARCHAR", values.value().toArray()));
                }
            });
        }
    }

    private static void addIntegerArray(
            List<String> assignments,
            List<SqlBinder> binders,
            String column,
            PatchValue<List<Integer>> values) {
        if (values.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, connection) -> {
                if (values.value() == null) {
                    statement.setNull(index, Types.ARRAY);
                } else {
                    statement.setArray(index, connection.createArrayOf("INTEGER", values.value().toArray()));
                }
            });
        }
    }

    private static void addBoolean(
            List<String> assignments,
            List<SqlBinder> binders,
            String column,
            PatchValue<Boolean> value) {
        if (value.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, ignored) -> {
                if (value.value() == null) {
                    statement.setNull(index, Types.BOOLEAN);
                } else {
                    statement.setBoolean(index, value.value());
                }
            });
        }
    }

    private static void addInstant(
            List<String> assignments,
            List<SqlBinder> binders,
            String column,
            PatchValue<Instant> value) {
        if (value.present()) {
            assignments.add("\"" + column + "\" = ?");
            binders.add((statement, index, ignored) -> timestamp(statement, index, value.value()));
        }
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(
                    index,
                    OffsetDateTime.ofInstant(value, ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
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

    public record UpdateAnnouncementCommand(
            String id,
            PatchValue<String> title,
            PatchValue<String> content,
            PatchValue<String> priority,
            PatchValue<List<String>> targetRoles,
            PatchValue<List<Integer>> targetYears,
            PatchValue<Boolean> isGlobal,
            PatchValue<Instant> publishAt,
            PatchValue<Instant> expiresAt,
            PatchValue<String> semesterId,
            PatchValue<String> sectionId,
            PatchValue<String> lecturerId,
            Instant updatedAt,
            int expectedVersion) {
    }

    public record TransitionCommand(
            String id,
            int expectedVersion,
            String archivedBy,
            Instant archivedAt,
            Instant updatedAt) {
    }

    public record PatchValue<T>(boolean present, T value) {

        public static <T> PatchValue<T> present(T value) {
            return new PatchValue<>(true, value);
        }

        public static <T> PatchValue<T> omitted() {
            return new PatchValue<>(false, null);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement, int index, Connection connection) throws SQLException;
    }
}
