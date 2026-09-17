package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import io.campuscore.restfulapi.web.DomainException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The faculty head role governs the thesis round lifecycle, so the role ceiling
 * has to reserve it for a super administrator — while still letting a plain
 * administrator save an unrelated field on an account that already holds it.
 */
class AdminRoleCeilingTest {

    private static final String TARGET = "target-user-id";
    private static final String ACTOR = "actor-user-id";

    @Test
    void plainAdminCannotMintAFacultyHeadButMayResubmitTheRoleUnchanged() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        Map<String, Boolean> rolesTheTargetHolds = new HashMap<>();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource parameters = invocation.getArgument(1);
                    String roleName = (String) parameters.getValue("roleName");
                    return Boolean.TRUE.equals(rolesTheTargetHolds.get(roleName)) ? List.of(1) : List.of();
                });

        AdminUserMutationService service = new AdminUserMutationService(
                jdbc, mock(PasswordEncoder.class), mock(AuthUserRepository.class));

        DomainException denied = assertThrows(DomainException.class,
                () -> service.update(TARGET, Map.of("role", "TRUONG_KHOA"), false, ACTOR));
        assertThat(denied.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getCode()).isEqualTo("ROLE_ESCALATION");

        // The account already holds the role, so an edit form echoing it back is
        // not a grant and must not be refused. Later stages of update() may still
        // fail for unrelated reasons; what is asserted is that the escalation
        // guard stays silent.
        rolesTheTargetHolds.put("TRUONG_KHOA", true);
        try {
            service.update(TARGET, Map.of("role", "TRUONG_KHOA"), false, ACTOR);
        } catch (DomainException unexpected) {
            assertNotEquals("ROLE_ESCALATION", unexpected.getCode(),
                    "re-sending the role an account already holds must not read as escalation");
        }
    }

    @Test
    void superAdminCanStillAssignTheFacultyHeadRole() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(List.of());

        AdminUserMutationService service = new AdminUserMutationService(
                jdbc, mock(PasswordEncoder.class), mock(AuthUserRepository.class));

        try {
            service.update(TARGET, Map.of("role", "TRUONG_KHOA"), true, ACTOR);
        } catch (DomainException unexpected) {
            assertNotEquals("ROLE_ESCALATION", unexpected.getCode());
        }
    }
}
