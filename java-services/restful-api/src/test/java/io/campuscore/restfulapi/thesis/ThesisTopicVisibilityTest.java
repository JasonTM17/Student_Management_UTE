package io.campuscore.restfulapi.thesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/** Draft/archived thesis topics are only visible to their owner and admins. */
class ThesisTopicVisibilityTest {

    private static final UUID ROUND_ID = UUID.fromString("22222222-2222-2222-2222-222222222101");

    private ThesisTopicRepository repository;
    private ThesisRoundReadPort rounds;
    private ThesisScopeRepository scope;
    private ThesisTopicService service;

    @BeforeEach
    void setUp() {
        repository = mock(ThesisTopicRepository.class);
        rounds = mock(ThesisRoundReadPort.class);
        scope = mock(ThesisScopeRepository.class);
        when(scope.findSupervisorsByTopicIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of());
        service = new ThesisTopicService(repository, rounds, scope);
    }

    @Test
    void studentNeverReceivesDraftListingsEvenWhenRepositoryRowsExist() {
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.DRAFT)))
                .thenReturn(List.of(draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002")));

        List<?> result = service.list(ROUND_ID, TopicStatus.DRAFT, jwt("student-user-101", "STUDENT"));

        assertEquals(0, result.size());
    }

    @Test
    void lecturerSeesOnlyOwnDraftsNotOtherSupervisorsProposals() {
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.DRAFT)))
                .thenReturn(List.of(
                        draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002"),
                        draftTopic("22222222-2222-2222-2222-222222222202", "lecturer-user-002")));

        List<?> result = service.list(ROUND_ID, TopicStatus.DRAFT, jwt("lecturer-user-002", "LECTURER"));

        assertEquals(2, result.size());
        assertEquals(0, service.list(ROUND_ID, TopicStatus.DRAFT, jwt("lecturer-user-003", "LECTURER")).size());
    }

    @Test
    void adminReceivesTheFullRequestedListing() {
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.DRAFT)))
                .thenReturn(List.of(draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002")));

        assertEquals(1, service.list(ROUND_ID, TopicStatus.DRAFT, jwt("admin-user", "ADMIN")).size());
    }

    @Test
    void superAdminReceivesTheFullRequestedListingAndCanReadDraft() {
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.DRAFT)))
                .thenReturn(List.of(draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002")));

        assertEquals(1, service.list(ROUND_ID, TopicStatus.DRAFT, jwt("super-admin-user", "SUPER_ADMIN")).size());

        ThesisTopic draft = draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002");
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));
        assertEquals(draft.getId(), service.get(draft.getId(), jwt("super-admin-user", "SUPER_ADMIN")).id());
    }

    @Test
    void hiddenTopicRespondsNotFoundToNonOwnerWhileOwnerAndAdminCanRead() {
        ThesisTopic draft = draftTopic("22222222-2222-2222-2222-222222222201", "lecturer-user-002");
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));

        ResponseStatusException hidden = assertThrows(ResponseStatusException.class,
                () -> service.get(draft.getId(), jwt("student-user-101", "STUDENT")));
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());

        assertEquals(draft.getId(), service.get(draft.getId(), jwt("lecturer-user-002", "LECTURER")).id());
        assertEquals(draft.getId(), service.get(draft.getId(), jwt("admin-user", "ADMIN")).id());
    }

    @Test
    void publishedTopicsStayReadableByEveryAuthenticatedCaller() throws ReflectiveOperationException {
        ThesisTopic published = draftTopic("22222222-2222-2222-2222-222222222202", "lecturer-user-002");
        var statusField = ThesisTopic.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(published, TopicStatus.PUBLISHED);
        when(repository.findById(published.getId())).thenReturn(Optional.of(published));

        assertEquals(published.getId(), service.get(published.getId(), jwt("student-user-101", "STUDENT")).id());
    }

    /**
     * Spec R12/R18: after the round publishes results a student reads the
     * outcome of the topic their approved group took part in — and only that
     * one. Other topics keep the outcome masked on the topic resource even
     * though the topic row itself stays readable.
     */
    @Test
    void publishedOutcomeStaysMaskedForStudentsOutsideTheAssignedGroup() throws ReflectiveOperationException {
        ThesisTopic published = draftTopic("22222222-2222-2222-2222-222222222202", "lecturer-user-002");
        setField(published, "status", TopicStatus.PUBLISHED);
        setField(published, "finalScore", new BigDecimal("8.50"));
        setField(published, "resultStatus", "GRADED");
        when(scope.countPublishedResultsRounds(ROUND_ID)).thenReturn(1);
        when(repository.findById(published.getId())).thenReturn(Optional.of(published));
        when(scope.findParticipatedTopicIds(eq("student-101"), eq(List.of(published.getId()))))
                .thenReturn(List.of());

        var outsider = service.get(published.getId(), studentJwt("student-user-101", "student-101"));
        assertNull(outsider.finalScore());
        assertNull(outsider.resultStatus());

        when(scope.findParticipatedTopicIds(eq("student-202"), eq(List.of(published.getId()))))
                .thenReturn(List.of(published.getId()));
        var participant = service.get(published.getId(), studentJwt("student-user-202", "student-202"));
        assertEquals(new BigDecimal("8.50"), participant.finalScore());
        assertEquals("GRADED", participant.resultStatus());
    }

    @Test
    void publishedOutcomeStaysMaskedOnTheListForStudentsOutsideTheAssignedGroup()
            throws ReflectiveOperationException {
        ThesisTopic published = draftTopic("22222222-2222-2222-2222-222222222202", "lecturer-user-002");
        setField(published, "status", TopicStatus.PUBLISHED);
        setField(published, "finalScore", new BigDecimal("8.50"));
        setField(published, "resultStatus", "GRADED");
        when(scope.countPublishedResultsRounds(ROUND_ID)).thenReturn(1);
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.PUBLISHED)))
                .thenReturn(List.of(published));
        when(repository.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.APPROVED)))
                .thenReturn(List.of());
        when(scope.findParticipatedTopicIds(eq("student-101"), eq(List.of(published.getId()))))
                .thenReturn(List.of());

        List<?> listing = service.list(ROUND_ID, null, studentJwt("student-user-101", "student-101"));

        assertEquals(1, listing.size());
        assertNull(((io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse) listing.get(0)).finalScore());
    }

    private static void setField(ThesisTopic topic, String name, Object value) throws ReflectiveOperationException {
        var field = ThesisTopic.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(topic, value);
    }

    private static Jwt studentJwt(String subject, String studentId) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .subject(subject)
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId)
                .build();
    }

    private static ThesisTopic draftTopic(String id, String createdBy) {
        ThesisTopic topic = new ThesisTopic(
                ROUND_ID,
                "department-demo",
                "Synthetic draft title",
                "Synthetic draft description used by the visibility test.",
                2,
                createdBy);
        // The entity assigns ids on persist; tests need a stable id, so set it
        // through the persistence-only path by round-tripping through save.
        return forceId(topic, UUID.fromString(id));
    }

    private static ThesisTopic forceId(ThesisTopic topic, UUID id) {
        try {
            var field = ThesisTopic.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(topic, id);
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
