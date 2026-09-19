package io.campuscore.restfulapi.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import io.campuscore.restfulapi.academic.service.AcademicConductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * LEC-P1-5: the conduct read must not become a directory of the student body.
 *
 * <p>The attendance endpoints already scoped a lecturer to their own sections, while
 * this one accepted any student id from any lecturer. The sweep's ledger initially
 * recorded this as fixed when it was not — the cited helper exists only in the
 * attendance service and no conduct file was touched — so the guard and this test
 * exist to make the claim checkable.
 *
 * <p>The deny paths are the security-relevant half and need no further stubbing,
 * because the guard runs before any record is assembled.
 */
class AcademicConductReadScopeTest {

    private static final String STUDENT = "student-profile-001";
    private static final String LECTURER_DIRECTORY_ID = "lecturer-001";

    private NamedParameterJdbcTemplate jdbc;
    private AcademicConductController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        controller = new AcademicConductController(new AcademicConductService(jdbc));
    }

    private void targetIsTaughtBy(int enrollmentMatches) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(enrollmentMatches);
        // Nothing else is reached on the deny paths.
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
    }

    @Test
    void lecturerWhoDoesNotTeachTheStudentIsRefused() {
        targetIsTaughtBy(0);

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> controller.getStudentConductSummary(STUDENT, lecturer( "LECTURER")));

        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(denied.getReason()).isEqualTo("CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
    }

    @Test
    void lecturerWithoutALecturerClaimIsRefusedRatherThanTrusted() {
        targetIsTaughtBy(1);

        // No lecturerId claim means the ownership question cannot be answered at all,
        // so the safe answer is refusal — not falling through to the full read.
        Jwt withoutClaim = Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .subject("lecturer-user-002")
                .claim("roles", List.of("LECTURER"))
                .build();

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> controller.getStudentConductSummary(STUDENT, withoutClaim));

        assertThat(denied.getReason()).isEqualTo("CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
    }

    @Test
    void lecturerTeachingTheStudentIsAllowed() {
        targetIsTaughtBy(1);

        // The guard passes; the record assembly then fails for lack of fixtures, so
        // what is asserted is that the refusal is not the ownership refusal.
        Throwable thrown = assertThrows(Throwable.class,
                () -> controller.getStudentConductSummary(STUDENT, lecturer("LECTURER")));

        if (thrown instanceof ResponseStatusException statusException) {
            assertThat(statusException.getReason())
                    .as("a teaching lecturer must not be refused on ownership grounds")
                    .isNotEqualTo("CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
        }
    }

    @Test
    void staffKeepTheFullRead() {
        targetIsTaughtBy(0);

        Throwable thrown = assertThrows(Throwable.class,
                () -> controller.getStudentConductSummary(STUDENT, lecturer("ADMIN")));

        if (thrown instanceof ResponseStatusException statusException) {
            assertThat(statusException.getReason())
                    .as("staff must not be scoped to section ownership")
                    .isNotEqualTo("CONDUCT_READ_REQUIRES_SECTION_OWNERSHIP");
        }
    }

    private static Jwt lecturer(String role) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .subject("lecturer-user-002")
                .claim("roles", List.of(role))
                .claim("lecturerId", LECTURER_DIRECTORY_ID)
                .build();
    }
}
