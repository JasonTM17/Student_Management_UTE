package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserMutationServiceTest {

    @Test
    void listBindsNullableFiltersAsVarcharForPostgres() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        AdminUserMutationService service =
                new AdminUserMutationService(jdbc, mock(PasswordEncoder.class));
        service.list(1, 10, null, null);

        org.mockito.ArgumentCaptor<MapSqlParameterSource> parameters =
                org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(anyString(), parameters.capture());

        assertThat(parameters.getValue().getSqlType("status")).isEqualTo(Types.VARCHAR);
        assertThat(parameters.getValue().getSqlType("search")).isEqualTo(Types.VARCHAR);
    }
}
