package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.academic.registration.repository.CurriculumDefaultRegistrationRepository;
import io.campuscore.restfulapi.academic.registration.service.CurriculumDefaultRegistrationService;
import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.academic.registration.dto.CurriculumDefaultRegistrationDtos.StudentRegistrationResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Real PostgreSQL schedules for competing curriculum-default registrations. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "deepseek.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@ActiveProfiles({"test", "persistence"})
class CurriculumDefaultRegistrationRacePostgresIT {
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private CurriculumDefaultRegistrationService defaults;
    @Autowired private RegistrationService registration;
    @MockitoSpyBean private CurriculumDefaultRegistrationRepository planRepository;

    @BeforeEach
    void resetRepositorySpy() {
        Mockito.reset(planRepository);
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> valueOr(
                "CAMPUSCORE_E2E_POSTGRES_URL", "jdbc:postgresql://127.0.0.1:5432/cx5_postgres_it"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void twoCurriculumRegistrationsCompeteForTheLastSeat() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 1);
        String first = fixture.seedStudent(1);
        String second = fixture.seedStudent(2);
        setUniqueAdmissionYear(fixture, List.of(first, second));
        mandatoryCourse(fixture);

        CountDownLatch plansReadSeat = new CountDownLatch(2);
        barrierForSeatRead(fixture, plansReadSeat, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<StudentRegistrationResponse> firstResult = pool.submit(() -> {
                ready.countDown();
                start.await();
                return defaults.registerStudent(first, fixture.semesterId, null, null);
            });
            Future<StudentRegistrationResponse> secondResult = pool.submit(() -> {
                ready.countDown();
                start.await();
                return defaults.registerStudent(second, fixture.semesterId, null, null);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            StudentRegistrationResponse firstResponse = firstResult.get(30, TimeUnit.SECONDS);
            StudentRegistrationResponse secondResponse = secondResult.get(30, TimeUnit.SECONDS);
            assertThat(firstResponse.registeredCount() + secondResponse.registeredCount()).isEqualTo(1);
            assertThat(firstResponse.skippedCount() + secondResponse.skippedCount()).isEqualTo(1);
            assertThat(scalar(fixture.sectionId, "enrolledCount")).isEqualTo(1);
            assertThat(activeEnrollmentCount(fixture.sectionId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"Enrollment\""
                            + " WHERE \"sectionId\"=:section AND \"registrationSource\"='CURRICULUM_DEFAULT'",
                    new MapSqlParameterSource("section", fixture.sectionId), Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void defaultAndManualRegistrationSerializeForTheSameCourse() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 1);
        String student = fixture.seedStudent(1);
        setUniqueAdmissionYear(fixture, List.of(student));
        mandatoryCourse(fixture);

        CountDownLatch planReadSeat = new CountDownLatch(1);
        CountDownLatch continueDefault = new CountDownLatch(1);
        barrierForSeatRead(fixture, planReadSeat, continueDefault);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<StudentRegistrationResponse> defaultResult = pool.submit(
                    () -> defaults.registerStudent(student, fixture.semesterId, null, null));
            assertThat(planReadSeat.await(15, TimeUnit.SECONDS)).isTrue();
            registration.enroll(student, fixture.sectionId, List.of("STUDENT"),
                    "cx5-manual-" + fixture.sectionId);
            continueDefault.countDown();

            StudentRegistrationResponse response = defaultResult.get(30, TimeUnit.SECONDS);
            assertThat(response.registeredCount()).isZero();
            assertThat(response.skippedCount()).isEqualTo(1);
            assertThat(activeEnrollmentCount(fixture.sectionId)).isEqualTo(1);
            assertThat(scalar(fixture.sectionId, "enrolledCount")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT \"registrationSource\" FROM academic.\"Enrollment\""
                            + " WHERE \"studentId\"=:student AND \"sectionId\"=:section AND \"status\"='ENROLLED'",
                    new MapSqlParameterSource().addValue("student", student).addValue("section", fixture.sectionId),
                    String.class)).isNull();
        } finally {
            continueDefault.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void barrierForSeatRead(AcademicEnrollmentPostgresFixture fixture, CountDownLatch reached,
            CountDownLatch release) {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<io.campuscore.restfulapi.academic.registration.model.CurriculumDefaultRegistrationRows.SectionRow> rows =
                    (List<io.campuscore.restfulapi.academic.registration.model.CurriculumDefaultRegistrationRows.SectionRow>)
                            invocation.callRealMethod();
            reached.countDown();
            if (release == null) {
                if (!reached.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent curriculum plans did not reach the seat-read barrier");
                }
            } else if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Manual enrollment did not release the curriculum plan barrier");
            }
            return rows;
        }).when(planRepository).findSections(eq(fixture.courseId), eq(fixture.semesterId));
    }

    private void mandatoryCourse(AcademicEnrollmentPostgresFixture fixture) {
        jdbc.update("INSERT INTO academic.\"CurriculumCourse\""
                        + " (\"id\",\"curriculumId\",\"courseId\",\"year\",\"semester\",\"isMandatory\")"
                        + " VALUES (:id,:curriculum,:course,1,1,TRUE)",
                new MapSqlParameterSource().addValue("id", "cc-" + fixture.courseId)
                        .addValue("curriculum", fixture.curriculumId).addValue("course", fixture.courseId));
    }

    private void setUniqueAdmissionYear(AcademicEnrollmentPostgresFixture fixture, List<String> studentIds) {
        int year = 1800;
        java.time.LocalDateTime start = java.time.LocalDateTime.of(year, 1, 1, 0, 0);
        java.time.LocalDateTime end = java.time.LocalDateTime.of(year + 1, 1, 1, 0, 0);
        while (academicYearsOverlapping(start, end) > 0) {
            year++;
            start = java.time.LocalDateTime.of(year, 1, 1, 0, 0);
            end = java.time.LocalDateTime.of(year + 1, 1, 1, 0, 0);
        }
        jdbc.update("UPDATE academic.\"AcademicYear\" SET \"year\"=:year,\"startDate\"=:start,"
                        + " \"endDate\"=:end,\"isCurrent\"=FALSE WHERE \"id\"=:id",
                new MapSqlParameterSource().addValue("year", year).addValue("start", start)
                        .addValue("end", end).addValue("id", fixture.academicYearId));
        jdbc.update("UPDATE academic.\"Semester\" SET \"startDate\"=:start,\"endDate\"=:end WHERE \"id\"=:id",
                new MapSqlParameterSource().addValue("start", start).addValue("end", end)
                        .addValue("id", fixture.semesterId));
        java.time.LocalDateTime admissionDate = start.plusMonths(8);
        for (String studentId : studentIds) {
            jdbc.update("UPDATE academic.\"Student\" SET \"admissionDate\"=:admissionDate WHERE \"id\"=:id",
                    new MapSqlParameterSource().addValue("admissionDate", admissionDate).addValue("id", studentId));
        }
    }

    private Integer academicYearsOverlapping(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"AcademicYear\""
                        + " WHERE \"startDate\"<:end AND \"endDate\">:start",
                new MapSqlParameterSource().addValue("start", start).addValue("end", end), Integer.class);
    }

    private Integer scalar(String sectionId, String column) {
        return jdbc.queryForObject("SELECT \"" + column + "\" FROM academic.\"Section\" WHERE \"id\"=:id",
                new MapSqlParameterSource("id", sectionId), Integer.class);
    }

    private Integer activeEnrollmentCount(String sectionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"Enrollment\""
                        + " WHERE \"sectionId\"=:id AND \"status\" IN ('ENROLLED','PENDING','CONFIRMED')",
                new MapSqlParameterSource("id", sectionId), Integer.class);
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
