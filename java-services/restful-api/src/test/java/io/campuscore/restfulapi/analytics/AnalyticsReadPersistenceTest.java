package io.campuscore.restfulapi.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.analytics-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AnalyticsReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareAnalyticsFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"public\"");
        createCountTable("Lecturer");
        createCountTable("Department");
        createCountTable("Faculty");
        createCountTable("Classroom");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "status" VARCHAR(40),
                    "year" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(80),
                    "name" VARCHAR(200),
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "credits" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(80),
                    "courseId" VARCHAR(120),
                    "semesterId" VARCHAR(120),
                    "lecturerId" VARCHAR(120),
                    "capacity" INTEGER,
                    "enrolledCount" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200),
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "academicYearId" VARCHAR(120),
                    "status" VARCHAR(80),
                    "startDate" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120),
                    "semesterId" VARCHAR(120),
                    "status" VARCHAR(40),
                    "enrolledAt" TIMESTAMP,
                    "letterGrade" VARCHAR(10),
                    "gradeStatus" VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Waitlist" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120),
                    "status" VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Invoice" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "semesterId" VARCHAR(120),
                    "status" VARCHAR(40) NOT NULL,
                    "total" DECIMAL(10, 2) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Payment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "invoiceId" VARCHAR(120),
                    "method" VARCHAR(80) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "amount" DECIMAL(10, 2) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Attendance" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120),
                    "sectionId" VARCHAR(120),
                    "date" TIMESTAMP,
                    "status" VARCHAR(40) NOT NULL,
                    "notes" VARCHAR(500),
                    "createdAt" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Notification" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "title" VARCHAR(200) NOT NULL,
                    "message" VARCHAR(2000) NOT NULL,
                    "type" VARCHAR(40) NOT NULL,
                    "link" VARCHAR(500),
                    "isRead" BOOLEAN NOT NULL DEFAULT FALSE,
                    "readAt" TIMESTAMP,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);

        for (String table : List.of(
                "Notification",
                "Attendance",
                "Payment",
                "Invoice",
                "Waitlist",
                "Student",
                "Lecturer",
                "Course",
                "Section",
                "Enrollment",
                "Department",
                "Faculty",
                "AcademicYear",
                "Semester",
                "Classroom")) {
            jdbc.update("DELETE FROM \"public\".\"" + table + "\"");
        }
    }

    @Test
    void overviewPreservesLegacyCountShapeForAdmins() throws Exception {
        insertRows("Student", 3);
        insertRows("Lecturer", 2);
        insertRows("Course", 4);
        insertRows("Section", 5);
        insertRows("Enrollment", 6);
        insertRows("Department", 2);
        insertRows("Faculty", 1);
        insertRows("AcademicYear", 2);
        insertRows("Semester", 3);
        insertRows("Classroom", 7);

        mvc.perform(get("/api/v1/analytics/overview").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(3))
                .andExpect(jsonPath("$.totalLecturers").value(2))
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.totalSections").value(5))
                .andExpect(jsonPath("$.totalEnrollments").value(6))
                .andExpect(jsonPath("$.totalDepartments").value(2))
                .andExpect(jsonPath("$.totalFaculties").value(1))
                .andExpect(jsonPath("$.totalAcademicYears").value(2))
                .andExpect(jsonPath("$.totalSemesters").value(3))
                .andExpect(jsonPath("$.totalClassrooms").value(7));
    }

    @Test
    void enrollmentsBySemesterPreservesLegacyOrderAndConfirmedCompletedFilter() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertAcademicYear("academic-year-2025", 2025);
        insertSemester(
                "semester-fall-2026",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "academic-year-2026",
                BASE_TIME.plusSeconds(86_400));
        insertSemester(
                "semester-spring-2026",
                "Spring 2026",
                "Spring 2026",
                "Hoc ky Xuan 2026",
                "academic-year-2026",
                BASE_TIME);
        insertSemester(
                "semester-fall-2025",
                "Fall 2025",
                "Fall 2025",
                "Hoc ky Thu 2025",
                "academic-year-2025",
                BASE_TIME.minusSeconds(86_400));
        insertEnrollmentInSemester("enrollment-fall-confirmed", "semester-fall-2026", "CONFIRMED");
        insertEnrollmentInSemester("enrollment-fall-completed", "semester-fall-2026", "COMPLETED");
        insertEnrollmentInSemester("enrollment-fall-pending", "semester-fall-2026", "PENDING");
        insertEnrollmentInSemester("enrollment-spring-completed", "semester-spring-2026", "COMPLETED");
        insertEnrollmentInSemester("enrollment-old-pending", "semester-fall-2025", "PENDING");

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].semesterId").value("semester-fall-2026"))
                .andExpect(jsonPath("$[0].semesterName").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameVi").value("Hoc ky Thu 2026"))
                .andExpect(jsonPath("$[0].academicYear").value(2026))
                .andExpect(jsonPath("$[0].enrollmentCount").value(2))
                .andExpect(jsonPath("$[1].semesterId").value("semester-spring-2026"))
                .andExpect(jsonPath("$[1].enrollmentCount").value(1));
    }

    @Test
    void sectionOccupancyPreservesLegacyOrderCountFallbackAndShape() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertSemester(
                "semester-fall-2026",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "academic-year-2026",
                BASE_TIME);
        insertCourse("course-web", "WEB101", "Web Programming", "Web Programming", "Lap trinh Web", 3);
        insertCourse("course-java", "JAVA201", "Java Backend", "Java Backend", "Java Backend", 4);
        insertSection("section-web-a", "A", "course-web", "semester-fall-2026", 10, 9);
        insertSection("section-java-b", "B", "course-java", "semester-fall-2026", 6, 3);
        insertEnrollmentInSection("enrollment-web-confirmed", "semester-fall-2026", "section-web-a", "CONFIRMED");
        insertEnrollmentInSection("enrollment-web-pending", "semester-fall-2026", "section-web-a", "PENDING");
        insertEnrollmentInSection("enrollment-web-completed", "semester-fall-2026", "section-web-a", "COMPLETED");

        mvc.perform(get("/api/v1/analytics/section-occupancy").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").value("section-web-a"))
                .andExpect(jsonPath("$[0].sectionNumber").value("A"))
                .andExpect(jsonPath("$[0].courseCode").value("WEB101"))
                .andExpect(jsonPath("$[0].courseName").value("Web Programming"))
                .andExpect(jsonPath("$[0].courseNameEn").value("Web Programming"))
                .andExpect(jsonPath("$[0].courseNameVi").value("Lap trinh Web"))
                .andExpect(jsonPath("$[0].semesterName").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameVi").value("Hoc ky Thu 2026"))
                .andExpect(jsonPath("$[0].capacity").value(10))
                .andExpect(jsonPath("$[0].enrolledCount").value(2))
                .andExpect(jsonPath("$[0].occupancyRate").value(20))
                .andExpect(jsonPath("$[1].sectionId").value("section-java-b"))
                .andExpect(jsonPath("$[1].enrolledCount").value(3))
                .andExpect(jsonPath("$[1].occupancyRate").value(50));
    }

    @Test
    void registrationPressurePreservesLegacySummaryHighestPressureAndWaitlistShape() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertSemesterWithStatus(
                "semester-registration",
                "Registration 2026",
                "Registration 2026",
                "Dang ky 2026",
                "academic-year-2026",
                "REGISTRATION_OPEN",
                BASE_TIME);
        insertSemesterWithStatus(
                "semester-add-drop",
                "Add Drop 2026",
                "Add Drop 2026",
                "Them bot 2026",
                "academic-year-2026",
                "ADD_DROP_OPEN",
                BASE_TIME.plusSeconds(86_400));
        insertSemesterWithStatus(
                "semester-closed",
                "Closed 2026",
                "Closed 2026",
                "Da dong 2026",
                "academic-year-2026",
                "CLOSED",
                BASE_TIME.minusSeconds(86_400));
        insertCourse("course-web", "WEB101", "Web Programming", "Web Programming", "Lap trinh Web", 3);
        insertCourse("course-java", "JAVA201", "Java Backend", "Java Backend", "Java Backend", 4);
        insertCourse("course-db", "DB301", "Databases", "Databases", "Co so du lieu", 3);
        insertSection("section-full", "A", "course-web", "semester-registration", 2, 0);
        insertSection("section-near", "B", "course-java", "semester-add-drop", 10, 8);
        insertSection("section-low", "C", "course-db", "semester-closed", 5, 0);
        insertEnrollmentInSection("enrollment-full-confirmed", "semester-registration", "section-full", "CONFIRMED");
        insertEnrollmentInSection("enrollment-full-pending", "semester-registration", "section-full", "PENDING");
        insertEnrollmentInSection("enrollment-full-completed", "semester-registration", "section-full", "COMPLETED");
        insertEnrollmentInSection("enrollment-low-pending", "semester-closed", "section-low", "PENDING");
        insertWaitlist("waitlist-full-active-1", "section-full", "ACTIVE");
        insertWaitlist("waitlist-full-active-2", "section-full", "ACTIVE");
        insertWaitlist("waitlist-near-active", "section-near", "ACTIVE");
        insertWaitlist("waitlist-low-cancelled", "section-low", "CANCELLED");

        mvc.perform(get("/api/v1/analytics/registration-pressure").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSemesters").value(2))
                .andExpect(jsonPath("$.totalSections").value(3))
                .andExpect(jsonPath("$.atCapacity").value(1))
                .andExpect(jsonPath("$.nearCapacity").value(1))
                .andExpect(jsonPath("$.waitlistActive").value(3))
                .andExpect(jsonPath("$.averageOccupancy").value(67))
                .andExpect(jsonPath("$.highestPressure.length()").value(3))
                .andExpect(jsonPath("$.highestPressure[0].sectionId").value("section-full"))
                .andExpect(jsonPath("$.highestPressure[0].sectionNumber").value("A"))
                .andExpect(jsonPath("$.highestPressure[0].courseCode").value("WEB101"))
                .andExpect(jsonPath("$.highestPressure[0].courseName").value("Web Programming"))
                .andExpect(jsonPath("$.highestPressure[0].semesterName").value("Registration 2026"))
                .andExpect(jsonPath("$.highestPressure[0].capacity").value(2))
                .andExpect(jsonPath("$.highestPressure[0].enrolledCount").value(2))
                .andExpect(jsonPath("$.highestPressure[0].waitlistCount").value(2))
                .andExpect(jsonPath("$.highestPressure[0].occupancyRate").value(100))
                .andExpect(jsonPath("$.highestPressure[1].sectionId").value("section-near"))
                .andExpect(jsonPath("$.highestPressure[1].enrolledCount").value(8))
                .andExpect(jsonPath("$.highestPressure[1].waitlistCount").value(1))
                .andExpect(jsonPath("$.highestPressure[1].occupancyRate").value(80))
                .andExpect(jsonPath("$.waitlistStatus.length()").value(2))
                .andExpect(jsonPath("$.waitlistStatus[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.waitlistStatus[0].count").value(3))
                .andExpect(jsonPath("$.waitlistStatus[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.waitlistStatus[1].count").value(1));
    }

    @Test
    void enrollmentTrendsPreservesLegacyMonthlyBucketsLabelsClampAndCounts() throws Exception {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth middleMonth = currentMonth.minusMonths(1);
        YearMonth oldestMonth = currentMonth.minusMonths(2);
        YearMonth outsideWindow = currentMonth.minusMonths(3);

        insertEnrollmentAt("enrollment-old-confirmed", "CONFIRMED", firstDay(outsideWindow));
        insertEnrollmentAt("enrollment-oldest-confirmed", "CONFIRMED", firstDay(oldestMonth).plusSeconds(60));
        insertEnrollmentAt("enrollment-oldest-completed", "COMPLETED", firstDay(oldestMonth).plusSeconds(120));
        insertEnrollmentAt("enrollment-middle-dropped", "DROPPED", firstDay(middleMonth).plusSeconds(60));
        insertEnrollmentAt("enrollment-middle-cancelled", "CANCELLED", firstDay(middleMonth).plusSeconds(120));
        insertEnrollmentAt("enrollment-current-confirmed", "CONFIRMED", firstDay(currentMonth).plusSeconds(60));
        insertEnrollmentAt("enrollment-current-pending", "PENDING", firstDay(currentMonth).plusSeconds(120));
        insertEnrollmentAt("enrollment-current-completed", "COMPLETED", firstDay(currentMonth).plusSeconds(180));
        insertEnrollmentAt("enrollment-current-dropped", "DROPPED", firstDay(currentMonth).plusSeconds(240));

        mvc.perform(get("/api/v1/analytics/enrollment-trends")
                        .queryParam("months", "3.8")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value(monthKey(oldestMonth)))
                .andExpect(jsonPath("$[0].year").value(oldestMonth.getYear()))
                .andExpect(jsonPath("$[0].monthNumber").value(oldestMonth.getMonthValue()))
                .andExpect(jsonPath("$[0].startDate").value(firstDay(oldestMonth).toString()))
                .andExpect(jsonPath("$[0].endDate").value(lastMillisecond(oldestMonth).toString()))
                .andExpect(jsonPath("$[0].labelEn").value(labelEn(oldestMonth)))
                .andExpect(jsonPath("$[0].labelVi").value(labelVi(oldestMonth)))
                .andExpect(jsonPath("$[0].enrolled").value(1))
                .andExpect(jsonPath("$[0].dropped").value(0))
                .andExpect(jsonPath("$[0].completed").value(1))
                .andExpect(jsonPath("$[0].net").value(2))
                .andExpect(jsonPath("$[0].totalActivity").value(2))
                .andExpect(jsonPath("$[1].month").value(monthKey(middleMonth)))
                .andExpect(jsonPath("$[1].enrolled").value(0))
                .andExpect(jsonPath("$[1].dropped").value(1))
                .andExpect(jsonPath("$[1].completed").value(0))
                .andExpect(jsonPath("$[1].net").value(-1))
                .andExpect(jsonPath("$[1].totalActivity").value(1))
                .andExpect(jsonPath("$[2].month").value(monthKey(currentMonth)))
                .andExpect(jsonPath("$[2].enrolled").value(2))
                .andExpect(jsonPath("$[2].dropped").value(1))
                .andExpect(jsonPath("$[2].completed").value(1))
                .andExpect(jsonPath("$[2].net").value(2))
                .andExpect(jsonPath("$[2].totalActivity").value(4));

        mvc.perform(get("/api/v1/analytics/enrollment-trends")
                        .queryParam("months", "abc")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void operatorSummaryPreservesLegacyDashboardLinksAndHealthShape() throws Exception {
        mvc.perform(get("/api/v1/analytics/operator-summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.serviceCount").value(8))
                .andExpect(jsonPath("$.dependencyDown").value(0))
                .andExpect(jsonPath("$.highLatency").value(0))
                .andExpect(jsonPath("$.dashboards.length()").value(4))
                .andExpect(jsonPath("$.dashboards[0].label").value("Grafana"))
                .andExpect(jsonPath("$.dashboards[0].url").value("http://127.0.0.1:3002"))
                .andExpect(jsonPath("$.dashboards[1].label").value("Prometheus"))
                .andExpect(jsonPath("$.dashboards[1].url").value("http://127.0.0.1:9090"))
                .andExpect(jsonPath("$.dashboards[2].label").value("Loki"))
                .andExpect(jsonPath("$.dashboards[2].url").value("http://127.0.0.1:3100"))
                .andExpect(jsonPath("$.dashboards[3].label").value("Tempo"))
                .andExpect(jsonPath("$.dashboards[3].url").value("http://127.0.0.1:3200"));
    }

    @Test
    void cockpitComposesLegacyAdminAnalyticsPayload() throws Exception {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        insertStudent("student-active", "ACTIVE", 1);
        insertRows("Lecturer", 1);
        insertRows("Department", 1);
        insertRows("Faculty", 1);
        insertRows("Classroom", 1);
        insertAcademicYear("academic-year-2026", 2026);
        insertSemesterWithStatus(
                "semester-registration",
                "Registration 2026",
                "Registration 2026",
                "Dang ky 2026",
                "academic-year-2026",
                "REGISTRATION_OPEN",
                firstDay(currentMonth));
        insertCourse("course-web", "WEB101", "Web Programming", "Web Programming", "Lap trinh Web", 3);
        insertSection("section-web-a", "A", "course-web", "semester-registration", 2, 0);
        insertEnrollmentInSection("enrollment-confirmed", "semester-registration", "section-web-a", "CONFIRMED");
        insertEnrollment("enrollment-completed-a", "COMPLETED", "A");
        insertWaitlist("waitlist-active", "section-web-a", "ACTIVE");
        insertInvoice("invoice-pending", "PENDING", BigDecimal.valueOf(100));
        insertPayment("payment-completed", "CARD", "COMPLETED", BigDecimal.valueOf(40));
        insertNotification(
                "notification-warning",
                "WARNING",
                false,
                BASE_TIME,
                "Capacity warning",
                "A section is near capacity.");

        mvc.perform(get("/api/v1/analytics/cockpit").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.overview.totalStudents").value(1))
                .andExpect(jsonPath("$.overview.totalLecturers").value(1))
                .andExpect(jsonPath("$.overview.totalCourses").value(1))
                .andExpect(jsonPath("$.overview.totalSections").value(1))
                .andExpect(jsonPath("$.overview.totalEnrollments").value(2))
                .andExpect(jsonPath("$.enrollmentTrends.length()").value(12))
                .andExpect(jsonPath("$.enrollmentTrends[11].month").value(monthKey(currentMonth)))
                .andExpect(jsonPath("$.enrollmentTrends[11].enrolled").value(1))
                .andExpect(jsonPath("$.enrollmentTrends[11].completed").value(1))
                .andExpect(jsonPath("$.enrollmentTrends[11].totalActivity").value(2))
                .andExpect(jsonPath("$.sectionOccupancy.length()").value(1))
                .andExpect(jsonPath("$.sectionOccupancy[0].sectionId").value("section-web-a"))
                .andExpect(jsonPath("$.sectionOccupancy[0].occupancyRate").value(50))
                .andExpect(jsonPath("$.gradeDistribution.length()").value(12))
                .andExpect(jsonPath("$.gradeDistribution[0].grade").value("A"))
                .andExpect(jsonPath("$.gradeDistribution[0].count").value(1))
                .andExpect(jsonPath("$.gradeDistribution[0].percentage").value(100))
                .andExpect(jsonPath("$.finance.totals.totalInvoiced").value(100.00))
                .andExpect(jsonPath("$.finance.totals.paidAmount").value(40.00))
                .andExpect(jsonPath("$.finance.totals.outstandingAmount").value(60.00))
                .andExpect(jsonPath("$.notifications.total").value(1))
                .andExpect(jsonPath("$.notifications.unread").value(1))
                .andExpect(jsonPath("$.notifications.recentAttention[0].id").value("notification-warning"))
                .andExpect(jsonPath("$.registrationPressure.activeSemesters").value(1))
                .andExpect(jsonPath("$.registrationPressure.totalSections").value(1))
                .andExpect(jsonPath("$.registrationPressure.waitlistActive").value(1))
                .andExpect(jsonPath("$.operator.serviceCount").value(8))
                .andExpect(jsonPath("$.operator.dashboards.length()").value(4));
    }

    @Test
    void topCoursesPreservesLegacySortLimitAndConfirmedPendingCounts() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertSemester(
                "semester-fall-2026",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "academic-year-2026",
                BASE_TIME);
        insertCourse("course-web", "WEB101", "Web Programming", "Web Programming", "Lap trinh Web", 3);
        insertCourse("course-java", "JAVA201", "Java Backend", "Java Backend", "Java Backend", 4);
        insertCourse("course-db", "DB301", "Databases", "Databases", "Co so du lieu", 3);
        insertSection("section-web-a", "A", "course-web", "semester-fall-2026", 30, 0);
        insertSection("section-web-b", "B", "course-web", "semester-fall-2026", 30, 0);
        insertSection("section-java-a", "A", "course-java", "semester-fall-2026", 20, 0);
        insertEnrollmentInSection("enrollment-web-confirmed", "semester-fall-2026", "section-web-a", "CONFIRMED");
        insertEnrollmentInSection("enrollment-web-pending", "semester-fall-2026", "section-web-a", "PENDING");
        insertEnrollmentInSection("enrollment-web-confirmed-2", "semester-fall-2026", "section-web-b", "CONFIRMED");
        insertEnrollmentInSection("enrollment-web-completed", "semester-fall-2026", "section-web-b", "COMPLETED");
        insertEnrollmentInSection("enrollment-java-pending", "semester-fall-2026", "section-java-a", "PENDING");

        mvc.perform(get("/api/v1/analytics/top-courses")
                        .queryParam("limit", "2")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseId").value("course-web"))
                .andExpect(jsonPath("$[0].courseCode").value("WEB101"))
                .andExpect(jsonPath("$[0].courseName").value("Web Programming"))
                .andExpect(jsonPath("$[0].courseNameEn").value("Web Programming"))
                .andExpect(jsonPath("$[0].courseNameVi").value("Lap trinh Web"))
                .andExpect(jsonPath("$[0].credits").value(3))
                .andExpect(jsonPath("$[0].sectionCount").value(2))
                .andExpect(jsonPath("$[0].totalEnrollments").value(3))
                .andExpect(jsonPath("$[1].courseId").value("course-java"))
                .andExpect(jsonPath("$[1].sectionCount").value(1))
                .andExpect(jsonPath("$[1].totalEnrollments").value(1));
    }

    @Test
    void studentStatisticsPreservesLegacyStatusTotalsAndYearBuckets() throws Exception {
        insertStudent("student-active-1", "ACTIVE", 1);
        insertStudent("student-active-2", "ACTIVE", 1);
        insertStudent("student-active-3", "ACTIVE", 2);
        insertStudent("student-graduated", "GRADUATED", 4);
        insertStudent("student-suspended", "SUSPENDED", 3);
        insertStudent("student-withdrawn", "WITHDRAWN", 2);

        mvc.perform(get("/api/v1/analytics/student-statistics").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.graduated").value(1))
                .andExpect(jsonPath("$.suspended").value(1))
                .andExpect(jsonPath("$.byYear.length()").value(4))
                .andExpect(jsonPath("$.byYear[0].year").value(1))
                .andExpect(jsonPath("$.byYear[0].count").value(2))
                .andExpect(jsonPath("$.byYear[1].year").value(2))
                .andExpect(jsonPath("$.byYear[1].count").value(2))
                .andExpect(jsonPath("$.byYear[2].year").value(3))
                .andExpect(jsonPath("$.byYear[2].count").value(1))
                .andExpect(jsonPath("$.byYear[3].year").value(4))
                .andExpect(jsonPath("$.byYear[3].count").value(1));
    }

    @Test
    void financeSummaryPreservesLegacyAggregatesAndFinanceOfficerAccess() throws Exception {
        insertInvoice("invoice-pending", "PENDING", BigDecimal.valueOf(1000));
        insertInvoice("invoice-overdue", "OVERDUE", BigDecimal.valueOf(700));
        insertInvoice("invoice-paid", "PAID", BigDecimal.valueOf(500));
        insertPayment("payment-card-completed", "CARD", "COMPLETED", BigDecimal.valueOf(450));
        insertPayment("payment-cash-completed", "CASH", "COMPLETED", BigDecimal.valueOf(50));
        insertPayment("payment-card-failed", "CARD", "FAILED", BigDecimal.valueOf(120));

        mvc.perform(get("/api/v1/analytics/finance-summary").with(financeOfficerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalInvoiced").value(2200.00))
                .andExpect(jsonPath("$.totals.paidAmount").value(500.00))
                .andExpect(jsonPath("$.totals.outstandingAmount").value(1700.00))
                .andExpect(jsonPath("$.totals.pendingInvoices").value(1))
                .andExpect(jsonPath("$.totals.overdueInvoices").value(1))
                .andExpect(jsonPath("$.totals.failedPayments").value(1))
                .andExpect(jsonPath("$.invoiceStatus.length()").value(3))
                .andExpect(jsonPath("$.paymentStatus.length()").value(2))
                .andExpect(jsonPath("$.providerFunnel.length()").value(3));
    }

    @Test
    void revenueAnalyticsPreservesLegacySemesterFilterAndFinanceOfficerAccess() throws Exception {
        insertInvoice("invoice-fall-paid", "semester-fall", "PAID", BigDecimal.valueOf(500));
        insertInvoice("invoice-fall-pending", "semester-fall", "PENDING", BigDecimal.valueOf(300));
        insertInvoice("invoice-spring-paid", "semester-spring", "PAID", BigDecimal.valueOf(700));
        insertPayment("payment-fall-completed", "invoice-fall-paid", "CARD", "COMPLETED", BigDecimal.valueOf(450));
        insertPayment("payment-fall-pending", "invoice-fall-pending", "CARD", "PENDING", BigDecimal.valueOf(200));
        insertPayment("payment-spring-completed", "invoice-spring-paid", "CASH", "COMPLETED", BigDecimal.valueOf(700));

        mvc.perform(get("/api/v1/analytics/revenue")
                        .queryParam("semesterId", "semester-fall")
                        .with(financeOfficerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").value(800.00))
                .andExpect(jsonPath("$.totalPaid").value(450.00))
                .andExpect(jsonPath("$.pending").value(350.00))
                .andExpect(jsonPath("$.invoiceCount").value(2))
                .andExpect(jsonPath("$.paidInvoiceCount").value(1))
                .andExpect(jsonPath("$.pendingInvoiceCount").value(1));

        mvc.perform(get("/api/v1/analytics/revenue").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").value(1500.00))
                .andExpect(jsonPath("$.totalPaid").value(1150.00))
                .andExpect(jsonPath("$.pending").value(350.00))
                .andExpect(jsonPath("$.invoiceCount").value(3))
                .andExpect(jsonPath("$.paidInvoiceCount").value(2))
                .andExpect(jsonPath("$.pendingInvoiceCount").value(1));
    }

    @Test
    void attendanceAnalyticsPreservesLegacySemesterFilterAndRateFormula() throws Exception {
        insertSection("section-fall", "A", "course-web", "semester-fall", 40, 0);
        insertSection("section-spring", "B", "course-web", "semester-spring", 40, 0);
        insertAttendance("attendance-fall-present", "section-fall", "PRESENT");
        insertAttendance("attendance-fall-late", "section-fall", "LATE");
        insertAttendance("attendance-fall-absent", "section-fall", "ABSENT");
        insertAttendance("attendance-fall-excused", "section-fall", "EXCUSED");
        insertAttendance("attendance-spring-present", "section-spring", "PRESENT");

        mvc.perform(get("/api/v1/analytics/attendance")
                        .queryParam("semesterId", "semester-fall")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(4))
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.late").value(1))
                .andExpect(jsonPath("$.excused").value(1))
                .andExpect(jsonPath("$.attendanceRate").value(50));

        mvc.perform(get("/api/v1/analytics/attendance").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(5))
                .andExpect(jsonPath("$.present").value(2))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.late").value(1))
                .andExpect(jsonPath("$.excused").value(1))
                .andExpect(jsonPath("$.attendanceRate").value(60));
    }

    @Test
    void lecturerAnalyticsPreservesLegacyClaimScopeCountsAndSectionBuckets() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertSemester(
                "semester-fall",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "academic-year-2026",
                BASE_TIME);
        insertCourse("course-java", "JAVA101", "Java Programming", "Java Programming", "Lap trinh Java", 3);
        insertCourse("course-web", "WEB101", "Web Programming", "Web Programming", "Lap trinh Web", 4);
        insertSectionWithLecturer("section-java", "01", "course-java", "semester-fall", "lecturer-1", 4, 0);
        insertSectionWithLecturer("section-web", "02", "course-web", "semester-fall", "lecturer-1", 5, 3);
        insertSectionWithLecturer("section-other", "03", "course-web", "semester-fall", "lecturer-2", 10, 10);
        insertEnrollmentInSectionWithGradeStatus(
                "enrollment-java-confirmed", "semester-fall", "section-java", "CONFIRMED", "PUBLISHED");
        insertEnrollmentInSectionWithGradeStatus(
                "enrollment-java-pending", "semester-fall", "section-java", "PENDING", "DRAFT");
        insertEnrollmentInSectionWithGradeStatus(
                "enrollment-java-completed", "semester-fall", "section-java", "COMPLETED", "PUBLISHED");
        insertEnrollmentInSectionWithGradeStatus(
                "enrollment-web-dropped", "semester-fall", "section-web", "DROPPED", "PUBLISHED");
        insertEnrollmentInSectionWithGradeStatus(
                "enrollment-other-confirmed", "semester-fall", "section-other", "CONFIRMED", "PUBLISHED");

        mvc.perform(get("/api/v1/analytics/lecturer/my").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSections").value(2))
                .andExpect(jsonPath("$.totalStudents").value(2))
                .andExpect(jsonPath("$.sectionsWithGrades").value(3));

        mvc.perform(get("/api/v1/analytics/lecturer/sections").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").value("section-java"))
                .andExpect(jsonPath("$[0].sectionNumber").value("01"))
                .andExpect(jsonPath("$[0].courseCode").value("JAVA101"))
                .andExpect(jsonPath("$[0].courseName").value("Java Programming"))
                .andExpect(jsonPath("$[0].courseNameEn").value("Java Programming"))
                .andExpect(jsonPath("$[0].courseNameVi").value("Lap trinh Java"))
                .andExpect(jsonPath("$[0].semesterName").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameVi").value("Hoc ky Thu 2026"))
                .andExpect(jsonPath("$[0].capacity").value(4))
                .andExpect(jsonPath("$[0].enrolledCount").value(2))
                .andExpect(jsonPath("$[0].occupancyRate").value(50))
                .andExpect(jsonPath("$[1].sectionId").value("section-web"))
                .andExpect(jsonPath("$[1].enrolledCount").value(3))
                .andExpect(jsonPath("$[1].occupancyRate").value(60));

        mvc.perform(get("/api/v1/analytics/lecturer/sections").with(lecturerJwt("lecturer-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sectionId").value("section-other"));
    }

    @Test
    void gradeDistributionPreservesLegacyBucketsAndPercentagesForAdmins() throws Exception {
        insertEnrollment("enrollment-a-1", "COMPLETED", "A");
        insertEnrollment("enrollment-a-2", "COMPLETED", "A");
        insertEnrollment("enrollment-b-plus", "COMPLETED", "B+");
        insertEnrollment("enrollment-f", "COMPLETED", "F");
        insertEnrollment("enrollment-confirmed", "CONFIRMED", "A");
        insertEnrollment("enrollment-ungraded", "COMPLETED", null);

        mvc.perform(get("/api/v1/analytics/grade-distribution").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].grade").value("A"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[1].grade").value("A-"))
                .andExpect(jsonPath("$[1].count").value(0))
                .andExpect(jsonPath("$[1].percentage").value(0))
                .andExpect(jsonPath("$[2].grade").value("B+"))
                .andExpect(jsonPath("$[2].count").value(1))
                .andExpect(jsonPath("$[2].percentage").value(25))
                .andExpect(jsonPath("$[11].grade").value("F"))
                .andExpect(jsonPath("$[11].count").value(1))
                .andExpect(jsonPath("$[11].percentage").value(25));
    }

    @Test
    void notificationSummaryPreservesLegacyAggregateShapeForAdmins() throws Exception {
        insertNotification(
                "notification-info-read",
                "INFO",
                true,
                BASE_TIME.minusSeconds(300),
                "Welcome",
                "Welcome message");
        insertNotification(
                "notification-warning",
                "WARNING",
                false,
                BASE_TIME.minusSeconds(100),
                "Capacity warning",
                "A section is near capacity.");
        insertNotification(
                "notification-error-new",
                "ERROR",
                false,
                BASE_TIME,
                "Delivery failed",
                "Email provider failed.");
        insertNotification(
                "notification-success",
                "SUCCESS",
                true,
                BASE_TIME.minusSeconds(200),
                "Payment posted",
                "Payment notification delivered.");

        mvc.perform(get("/api/v1/analytics/notification-summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.read").value(2))
                .andExpect(jsonPath("$.byType.length()").value(4))
                .andExpect(jsonPath("$.byType[0].type").value("ERROR"))
                .andExpect(jsonPath("$.byType[0].count").value(1))
                .andExpect(jsonPath("$.recentAttention.length()").value(2))
                .andExpect(jsonPath("$.recentAttention[0].id").value("notification-error-new"))
                .andExpect(jsonPath("$.recentAttention[0].title").value("Delivery failed"))
                .andExpect(jsonPath("$.recentAttention[0].message").value("Email provider failed."))
                .andExpect(jsonPath("$.recentAttention[0].type").value("ERROR"))
                .andExpect(jsonPath("$.recentAttention[0].createdAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.recentAttention[1].id").value("notification-warning"));
    }

    @Test
    void analyticsReadBoundaryFailsClosedForAnonymousRolesAndUnexpectedQueries() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/analytics/overview").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/finance-summary").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/revenue").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/attendance").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/lecturer/my").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/lecturer/sections").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/lecturer/my")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-user")
                                .claim("roles", List.of("LECTURER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/section-occupancy").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/registration-pressure").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/enrollment-trends").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/operator-summary").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/cockpit").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/top-courses").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/student-statistics").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/grade-distribution").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/notification-summary").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/overview")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/revenue")
                        .queryParam("semesterId", "semester-fall")
                        .queryParam("semesterId", "semester-spring")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/revenue")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/attendance")
                        .queryParam("semesterId", "semester-fall")
                        .queryParam("semesterId", "semester-spring")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/attendance")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/lecturer/my")
                        .queryParam("months", "12")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/lecturer/sections")
                        .queryParam("semesterId", "semester-1")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/section-occupancy")
                        .queryParam("semesterId", "semester-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/registration-pressure")
                        .queryParam("semesterId", "semester-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/enrollment-trends")
                        .queryParam("months", "3")
                        .queryParam("months", "4")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/operator-summary")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/cockpit")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/top-courses")
                        .queryParam("limit", "2")
                        .queryParam("limit", "3")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/student-statistics")
                        .queryParam("year", "1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/grade-distribution")
                        .queryParam("semesterId", "semester-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/notification-summary")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void createCountTable(String table) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"public\".\"" + table + "\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY)");
    }

    private void insertRows(String table, int count) {
        for (int index = 1; index <= count; index++) {
            jdbc.update(
                    "INSERT INTO \"public\".\"" + table + "\" (\"id\") VALUES (?)",
                    table.toLowerCase() + "-" + index);
        }
    }

    private void insertAcademicYear(String id, int year) {
        jdbc.update(
                "INSERT INTO \"public\".\"AcademicYear\" (\"id\", \"year\") VALUES (?, ?)",
                id,
                year);
    }

    private void insertStudent(String id, String status, int year) {
        jdbc.update(
                "INSERT INTO \"public\".\"Student\" (\"id\", \"status\", \"year\") VALUES (?, ?, ?)",
                id,
                status,
                year);
    }

    private void insertSemester(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String academicYearId,
            Instant startDate) {
        jdbc.update(
                "INSERT INTO \"public\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"academicYearId\", \"startDate\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                academicYearId,
                localDateTime(startDate));
    }

    private void insertSemesterWithStatus(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String academicYearId,
            String status,
            Instant startDate) {
        jdbc.update(
                "INSERT INTO \"public\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"academicYearId\", \"status\", \"startDate\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                academicYearId,
                status,
                localDateTime(startDate));
    }

    private void insertCourse(String id, String code, String name, String nameEn, String nameVi, int credits) {
        jdbc.update(
                "INSERT INTO \"public\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                code,
                name,
                nameEn,
                nameVi,
                credits);
    }

    private void insertSection(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            int capacity,
            int enrolledCount) {
        insertSectionWithLecturer(id, sectionNumber, courseId, semesterId, null, capacity, enrolledCount);
    }

    private void insertSectionWithLecturer(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            int capacity,
            int enrolledCount) {
        jdbc.update(
                "INSERT INTO \"public\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\","
                        + " \"capacity\", \"enrolledCount\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionNumber,
                courseId,
                semesterId,
                lecturerId,
                capacity,
                enrolledCount);
    }

    private void insertInvoice(String id, String status, BigDecimal total) {
        jdbc.update(
                "INSERT INTO \"public\".\"Invoice\""
                        + " (\"id\", \"status\", \"total\", \"createdAt\") VALUES (?, ?, ?, ?)",
                id,
                status,
                total,
                localDateTime(BASE_TIME));
    }

    private void insertInvoice(String id, String semesterId, String status, BigDecimal total) {
        jdbc.update(
                "INSERT INTO \"public\".\"Invoice\""
                        + " (\"id\", \"semesterId\", \"status\", \"total\", \"createdAt\") VALUES (?, ?, ?, ?, ?)",
                id,
                semesterId,
                status,
                total,
                localDateTime(BASE_TIME));
    }

    private void insertEnrollment(String id, String status, String letterGrade) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"letterGrade\","
                        + " \"gradeStatus\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                null,
                null,
                status,
                localDateTime(BASE_TIME),
                letterGrade,
                null);
    }

    private void insertEnrollmentInSemester(String id, String semesterId, String status) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"letterGrade\","
                        + " \"gradeStatus\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                null,
                semesterId,
                status,
                localDateTime(BASE_TIME),
                null,
                null);
    }

    private void insertEnrollmentInSection(String id, String semesterId, String sectionId, String status) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"letterGrade\","
                        + " \"gradeStatus\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionId,
                semesterId,
                status,
                localDateTime(BASE_TIME),
                null,
                null);
    }

    private void insertEnrollmentInSectionWithGradeStatus(
            String id,
            String semesterId,
            String sectionId,
            String status,
            String gradeStatus) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"letterGrade\","
                        + " \"gradeStatus\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionId,
                semesterId,
                status,
                localDateTime(BASE_TIME),
                null,
                gradeStatus);
    }

    private void insertEnrollmentAt(String id, String status, Instant enrolledAt) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"letterGrade\","
                        + " \"gradeStatus\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                null,
                null,
                status,
                localDateTime(enrolledAt),
                null,
                null);
    }

    private void insertWaitlist(String id, String sectionId, String status) {
        jdbc.update(
                "INSERT INTO \"public\".\"Waitlist\" (\"id\", \"sectionId\", \"status\") VALUES (?, ?, ?)",
                id,
                sectionId,
                status);
    }

    private void insertPayment(String id, String method, String status, BigDecimal amount) {
        jdbc.update(
                "INSERT INTO \"public\".\"Payment\""
                        + " (\"id\", \"method\", \"status\", \"amount\", \"createdAt\") VALUES (?, ?, ?, ?, ?)",
                id,
                method,
                status,
                amount,
                localDateTime(BASE_TIME));
    }

    private void insertPayment(String id, String invoiceId, String method, String status, BigDecimal amount) {
        jdbc.update(
                "INSERT INTO \"public\".\"Payment\""
                        + " (\"id\", \"invoiceId\", \"method\", \"status\", \"amount\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                invoiceId,
                method,
                status,
                amount,
                localDateTime(BASE_TIME));
    }

    private void insertAttendance(String id, String sectionId, String status) {
        jdbc.update(
                "INSERT INTO \"public\".\"Attendance\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"date\", \"status\", \"notes\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                "student-user",
                sectionId,
                localDateTime(BASE_TIME),
                status,
                null,
                localDateTime(BASE_TIME));
    }

    private void insertNotification(
            String id,
            String type,
            boolean read,
            Instant createdAt,
            String title,
            String message) {
        jdbc.update(
                "INSERT INTO \"public\".\"Notification\""
                        + " (\"id\", \"userId\", \"title\", \"message\", \"type\", \"link\","
                        + " \"isRead\", \"readAt\", \"createdAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "student-user",
                title,
                message,
                type,
                null,
                read,
                read ? localDateTime(createdAt.plusSeconds(1)) : null,
                localDateTime(createdAt));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .subject("admin-user")
                .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor financeOfficerJwt() {
        return jwt().jwt(token -> token
                .subject("finance-user")
                .claim("roles", List.of("FINANCE_OFFICER")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER"));
    }

    private static RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                .subject("student-user")
                .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                .subject("lecturer-user")
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private static LocalDateTime localDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant firstDay(YearMonth month) {
        return month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant lastMillisecond(YearMonth month) {
        return firstDay(month.plusMonths(1)).minusMillis(1);
    }

    private static String monthKey(YearMonth month) {
        return "%d-%02d".formatted(month.getYear(), month.getMonthValue());
    }

    private static String labelEn(YearMonth month) {
        return month.getMonth().name().substring(0, 1)
                + month.getMonth().name().substring(1, 3).toLowerCase()
                + " "
                + month.getYear();
    }

    private static String labelVi(YearMonth month) {
        return "tháng " + month.getMonthValue() + " năm " + month.getYear();
    }
}
