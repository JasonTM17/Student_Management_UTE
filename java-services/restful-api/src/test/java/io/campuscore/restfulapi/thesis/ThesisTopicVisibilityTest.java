package io.campuscore.restfulapi.thesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/** Draft/archived thesis topics are only visible to their owner and admins. */
class ThesisTopicVisibilityTest {

    private static final UUID ROUND_ID = UUID.fromString("22222222-2222-2222-2222-222222222101");

    private ThesisTopicRepository repository;
    private ThesisRoundReadPort rounds;
    private ThesisTopicService service;

    @BeforeEach
    void setUp() {
        repository = mock(ThesisTopicRepository.class);
        rounds = mock(ThesisRoundReadPort.class);
        service = new ThesisTopicService(repository, rounds);
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
