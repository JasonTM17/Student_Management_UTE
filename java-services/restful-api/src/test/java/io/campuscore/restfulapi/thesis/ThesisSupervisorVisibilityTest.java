package io.campuscore.restfulapi.thesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import io.campuscore.restfulapi.web.DomainException;

/**
 * The supervisor list is readable by students so their group view can show the
 * supervisor's name, which means it must not become a way to enumerate drafts or
 * to harvest staff email addresses.
 */
class ThesisSupervisorVisibilityTest {

    private static final UUID ROUND_ID = UUID.fromString("22222222-2222-2222-2222-222222222101");
    private static final String TOPIC_ID = "22222222-2222-2222-2222-222222222201";
    private static final String OWNER = "lecturer-user-002";
    private static final String LECTURER_DIRECTORY_ID = "lecturer-001";
    private static final String SUPERVISOR_EMAIL = "supervisor@campuscore.edu";

    private NamedParameterJdbcTemplate jdbc;
    private ThesisTopicRepository repository;
    private ThesisSupervisorService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = mock(ThesisTopicRepository.class);
        service = new ThesisSupervisorService(jdbc, repository);

        ResultSet rows = mock(ResultSet.class);
        when(rows.getString("lecturer_id")).thenReturn(LECTURER_DIRECTORY_ID);
        when(rows.getInt("supervisor_order")).thenReturn(1);
        when(rows.getString("first_name")).thenReturn("An");
        when(rows.getString("last_name")).thenReturn("Nguyễn");
        // Not fetched on the redacted path, so the stub is permissive.
        lenient().when(rows.getString("email")).thenReturn(SUPERVISOR_EMAIL);

        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<ThesisSupervisorService.SupervisorRow> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(rows, 0));
                });
    }

    @Test
    void studentCannotReadSupervisorsOfADraftTopic() {
        ThesisTopic draft = topic();
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));

        DomainException hidden = assertThrows(DomainException.class,
                () -> service.list(draft.getId(), jwt("student-user-101", "STUDENT")));

        // 404 rather than 403: a 403 would confirm the draft id exists.
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatus());
    }

    @Test
    void studentReadingAPublishedTopicGetsTheNameButNotTheEmailAddress() {
        ThesisTopic published = topic();
        published.publish();
        when(repository.findById(published.getId())).thenReturn(Optional.of(published));

        List<ThesisSupervisorService.SupervisorRow> rows =
                service.list(published.getId(), jwt("student-user-101", "STUDENT"));

        assertEquals(1, rows.size());
        assertEquals("An", rows.get(0).firstName());
        assertEquals("Nguyễn", rows.get(0).lastName());
        assertNull(rows.get(0).email(), "student responses must not carry supervisor contact details");
    }

    @Test
    void ownerAndStaffKeepContactDetailsAndDraftAccess() {
        ThesisTopic draft = topic();
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertEquals(SUPERVISOR_EMAIL,
                service.list(draft.getId(), jwt(OWNER, "LECTURER")).get(0).email());
        assertEquals(SUPERVISOR_EMAIL,
                service.list(draft.getId(), jwt("admin-user", "ADMIN")).get(0).email());
    }

    private static ThesisTopic topic() {
        ThesisTopic topic = new ThesisTopic(
                ROUND_ID,
                "department-demo",
                "Synthetic topic title",
                "Synthetic topic description used by the supervisor visibility test.",
                2,
                OWNER);
        try {
            var field = ThesisTopic.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(topic, UUID.fromString(TOPIC_ID));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return topic;
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .subject(subject)
                .claim("roles", List.of(role))
                .build();
    }
}
