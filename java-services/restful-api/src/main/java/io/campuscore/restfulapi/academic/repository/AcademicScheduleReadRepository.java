package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ClassroomResponse;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.SectionResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SELECT-only adapter for legacy Prisma academic section schedule tables. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-schedule-read", name = "enabled", havingValue = "true")
public class AcademicScheduleReadRepository {

    private static final String SCHEDULE = "\"academic\".\"SectionSchedule\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";
    private static final RowMapper<ScheduleResponse> SCHEDULE_ROW_MAPPER =
            AcademicScheduleReadRepository::mapSchedule;

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicScheduleReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScheduleResponse> findSchedules(long offset, int limit) {
        return jdbc.query(scheduleSelect()
                        + " ORDER BY schedule.\"dayOfWeek\" ASC, schedule.\"startTime\" ASC, schedule.\"id\" ASC"
                        + " LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit),
                SCHEDULE_ROW_MAPPER);
    }

    public long countSchedules() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEDULE,
                new MapSqlParameterSource(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<ScheduleResponse> findScheduleById(String id) {
        return jdbc.query(
                        scheduleSelect() + " WHERE schedule.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        SCHEDULE_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
    }

    private static String scheduleSelect() {
        return "SELECT schedule.\"id\", schedule.\"sectionId\", schedule.\"classroomId\","
                + " schedule.\"dayOfWeek\", schedule.\"startTime\", schedule.\"endTime\","
                + " section.\"sectionNumber\", section.\"courseId\", section.\"semesterId\","
                + " section.\"lecturerId\", section.\"capacity\", section.\"enrolledCount\","
                + " section.\"status\" AS section_status,"
                + " classroom.\"building\", classroom.\"roomNumber\""
                + " FROM " + SCHEDULE + " schedule"
                + " JOIN " + SECTION + " section ON section.\"id\" = schedule.\"sectionId\""
                + " JOIN " + CLASSROOM + " classroom ON classroom.\"id\" = schedule.\"classroomId\"";
    }

    private static ScheduleResponse mapSchedule(ResultSet resultSet, int ignored)
            throws SQLException {
        String sectionId = resultSet.getString("sectionId");
        String classroomId = resultSet.getString("classroomId");
        return new ScheduleResponse(
                resultSet.getString("id"),
                sectionId,
                classroomId,
                resultSet.getInt("dayOfWeek"),
                resultSet.getString("startTime"),
                resultSet.getString("endTime"),
                new SectionResponse(
                        sectionId,
                        resultSet.getString("sectionNumber"),
                        resultSet.getString("courseId"),
                        resultSet.getString("semesterId"),
                        resultSet.getString("lecturerId"),
                        classroomId,
                        resultSet.getInt("capacity"),
                        resultSet.getInt("enrolledCount"),
                        resultSet.getString("section_status")),
                new ClassroomResponse(
                        classroomId,
                        resultSet.getString("building"),
                        resultSet.getString("roomNumber")));
    }
}
