package io.campuscore.restfulapi.academic.registration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CurriculumDefaultRegistrationRepositoryTest {

    @Test
    void passedCourseIdsIncludePublishedAndAppealedPassingGradesOnly() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:curriculum_default_registration_passed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA academic");
        jdbc.execute("CREATE TABLE academic.\"Enrollment\" (\"id\" VARCHAR(40), \"studentId\" VARCHAR(40),"
                + " \"courseId\" VARCHAR(40), \"status\" VARCHAR(40), \"gradeStatus\" VARCHAR(40),"
                + " \"letterGrade\" VARCHAR(10))");
        jdbc.update("INSERT INTO academic.\"Enrollment\" VALUES"
                        + " ('published-pass', 'student-1', 'course-published', 'COMPLETED', 'PUBLISHED', 'B'),"
                        + " ('appealed-pass', 'student-1', 'course-appealed', 'COMPLETED', 'APPEALED', 'A'),"
                        + " ('published-fail', 'student-1', 'course-fail', 'COMPLETED', 'PUBLISHED', 'F'),"
                        + " ('published-withdrawal', 'student-1', 'course-withdrawal', 'COMPLETED', 'PUBLISHED', 'W'),"
                        + " ('draft-pass', 'student-1', 'course-draft', 'COMPLETED', 'DRAFT', 'B'),"
                        + " ('other-student', 'student-2', 'course-other', 'COMPLETED', 'PUBLISHED', 'A')");

        CurriculumDefaultRegistrationRepository repository = new CurriculumDefaultRegistrationRepository(
                new NamedParameterJdbcTemplate(dataSource));

        assertThat(repository.findPassedCourseIds("student-1"))
                .containsExactlyInAnyOrder("course-published", "course-appealed");
    }
}
