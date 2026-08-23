package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class AcademicScheduleReadPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void scheduleListPreservesLegacyEnvelopeHydrationPaginationAndOrdering() throws Exception {
        mvc.perform(get("/api/v1/schedules")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("schedule-early"))
                .andExpect(jsonPath("$.data[0].sectionId").value("section-2"))
                .andExpect(jsonPath("$.data[0].classroom.roomNumber").value("B202"))
                .andExpect(jsonPath("$.data[0].section.sectionNumber").value("B"))
                .andExpect(jsonPath("$.data[0].section.courseId").value("course-2"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void scheduleDetailReturnsHydratedSectionAndClassroomOrNotFound() throws Exception {
        mvc.perform(get("/api/v1/schedules/schedule-late").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("schedule-late"))
                .andExpect(jsonPath("$.dayOfWeek").value(5))
                .andExpect(jsonPath("$.startTime").value("13:00"))
                .andExpect(jsonPath("$.endTime").value("15:00"))
                .andExpect(jsonPath("$.section.status").value("OPEN"))
                .andExpect(jsonPath("$.classroom.building").value("A"));

        mvc.perform(get("/api/v1/schedules/missing").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    @Test
    void scheduleReadBoundaryFailsClosedForAnonymousAndInvalidQueries() throws Exception {
        mvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/schedules")
                        .queryParam("limit", "101")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/schedules")
                        .queryParam("courseId", "course-1")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Classroom" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "building" VARCHAR(120) NOT NULL,
                    "roomNumber" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."SectionSchedule" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "classroomId" VARCHAR(120) NOT NULL,
                    "dayOfWeek" INTEGER NOT NULL,
                    "startTime" VARCHAR(20) NOT NULL,
                    "endTime" VARCHAR(20) NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
    }

    private void insertFixture() {
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                "room-1", "A", "A101");
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                "room-2", "B", "B202");
        insertSection("section-1", "A", "course-1", "room-1", 30, 12);
        insertSection("section-2", "B", "course-2", "room-2", 25, 20);
        insertSchedule("schedule-late", "section-1", "room-1", 5, "13:00", "15:00");
        insertSchedule("schedule-early", "section-2", "room-2", 2, "08:00", "10:00");
    }

    private void insertSection(
            String id,
            String sectionNumber,
            String courseId,
            String classroomId,
            int capacity,
            int enrolledCount) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\","
                        + " \"classroomId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionNumber,
                courseId,
                "semester-1",
                "lecturer-1",
                classroomId,
                capacity,
                enrolledCount,
                "OPEN");
    }

    private void insertSchedule(
            String id,
            String sectionId,
            String classroomId,
            int dayOfWeek,
            String startTime,
            String endTime) {
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                sectionId,
                classroomId,
                dayOfWeek,
                startTime,
                endTime);
    }
}
