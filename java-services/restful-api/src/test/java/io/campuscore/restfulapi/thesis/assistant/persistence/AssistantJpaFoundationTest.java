package io.campuscore.restfulapi.thesis.assistant.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class AssistantJpaFoundationTest {

    @Test
    void mapsEveryV11V12TableToTheAssistantSchema() {
        assertTable(AssistantKnowledgeDocumentEntity.class, "knowledge_document");
        assertTable(AssistantKnowledgeRevisionEntity.class, "knowledge_document_revision");
        assertTable(AssistantKnowledgeAuditEntity.class, "knowledge_document_audit");
        assertTable(AssistantConversationEntity.class, "chat_conversation");
        assertTable(AssistantMessageEntity.class, "chat_message");
        assertTable(AssistantCitationEntity.class, "chat_citation");
        assertTable(AssistantUsageBucketEntity.class, "usage_bucket");
        assertTable(AssistantTurnLedgerEntity.class, "chat_turn_ledger");
        assertTable(AssistantProviderDispatchEntity.class, "provider_dispatch_registry");
        assertTable(AssistantFeedbackEntity.class, "chat_message_feedback");
    }

    @Test
    void mutableGovernanceBoundariesDeclarePessimisticLocks() throws Exception {
        assertThat(AssistantKnowledgeDocumentJpaRepository.class
                .getMethod("findLockedById", UUID.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(AssistantKnowledgeRevisionJpaRepository.class
                .getMethod("findOwnDraftsForUpdate", UUID.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(AssistantConversationJpaRepository.class
                .getMethod("findLockedByIdAndOwnerId", UUID.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(AssistantTurnLedgerJpaRepository.class
                .getMethod("findLockedByTurnIdAndOwnerId", UUID.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void entityFactoriesPreserveOwnerAndReviewInvariants() {
        UUID documentId = UUID.randomUUID();
        AssistantKnowledgeDocumentEntity document = AssistantKnowledgeDocumentEntity.draft(
                documentId, "jpa-test", "en", "Title", "Public content", "office", 10,
                java.time.Instant.parse("2026-08-25T00:00:00Z"));
        assertThat(document.isActive()).isTrue();
        assertThat(document.getVisibility()).isEqualTo("PUBLIC");

        AssistantKnowledgeRevisionEntity revision = AssistantKnowledgeRevisionEntity.draft(
                UUID.randomUUID(), documentId, 1, "en", "jpa-test", "Title", "Public content",
                "office", 10, "admin-a", java.time.Instant.parse("2026-08-25T00:00:00Z"));
        revision.submit();
        assertThat(revision.getState()).isEqualTo("PENDING_REVIEW");
        assertThatThrownBy(() -> revision.publish("admin-a", java.time.Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        revision.publish("admin-b", java.time.Instant.now());
        assertThat(revision.getReviewedBy()).isEqualTo("admin-b");
        assertThat(revision.getState()).isEqualTo("PUBLISHED");
    }

    @Test
    void writerRejectsSensitiveKnowledgeBeforePersistence() {
        assertThatThrownBy(() -> AssistantKnowledgeJpaWriter.validatePublic(
                "public-slug",
                "en",
                "Contact student@example.test",
                "Public content",
                "office"))
                .isInstanceOf(AssistantKnowledgeJpaWriter.KnowledgePersistenceException.class)
                .extracting("code")
                .isEqualTo("KNOWLEDGE_PRIVACY_REJECTED");
    }

    private static void assertTable(Class<?> type, String name) {
        Table table = type.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.schema()).isEqualTo("assistant");
        assertThat(table.name()).isEqualTo(name);
    }
}
