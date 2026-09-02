package io.campuscore.restfulapi.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.engagement.repository.AnnouncementReadRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementReadRepository.UserVisibility;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AnnouncementReadRepositoryTest {

    @Test
    void userVisibilityBindsPostgresTimezoneParameter() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        AnnouncementReadRepository repository = new AnnouncementReadRepository(jdbc);
        repository.findForUser(
                new UserVisibility(
                        List.of("LECTURER"),
                        null,
                        null,
                        "lecturer-1",
                        Instant.parse("2026-08-31T10:00:00Z")),
                0,
                5);

        org.mockito.ArgumentCaptor<MapSqlParameterSource> parameters =
                org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), parameters.capture(), any(RowMapper.class));

        assertThat(parameters.getValue().getSqlType("now"))
                .isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(parameters.getValue().getValue("now"))
                .isEqualTo(OffsetDateTime.parse("2026-08-31T10:00:00Z"));
    }

    @Test
    void rowMapperReadsPostgresTimestampWithTimezoneAsInstant() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        AnnouncementReadRepository repository = new AnnouncementReadRepository(jdbc);
        repository.findForUser(
                new UserVisibility(
                        List.of("LECTURER"),
                        null,
                        null,
                        "lecturer-1",
                        Instant.parse("2026-08-31T10:00:00Z")),
                0,
                5);

        org.mockito.ArgumentCaptor<RowMapper<AnnouncementResponse>> mapper =
                org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), any(MapSqlParameterSource.class), mapper.capture());

        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-31T10:00:00+07:00");
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(anyString(), eq(OffsetDateTime.class))).thenReturn(timestamp);
        when(resultSet.getObject(anyString(), eq(java.time.LocalDateTime.class)))
                .thenThrow(new AssertionError("PostgreSQL timestamptz must not be read as LocalDateTime"));

        AnnouncementResponse response = mapper.getValue().mapRow(resultSet, 0);

        assertThat(response.publishAt()).isEqualTo(timestamp.toInstant());
        assertThat(response.createdAt()).isEqualTo(timestamp.toInstant());
    }
}
