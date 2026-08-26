package io.campuscore.restfulapi.thesis.assistant.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AssistantKnowledgeJpaWriterPersistenceTest {

    @Autowired
    private AssistantKnowledgeJpaWriter writer;

    @Autowired
    private AssistantKnowledgeDocumentJpaRepository documents;

    @Test
    void draftSubmitPublishUsesSecondReviewerAndPublicProjection() {
        String slug = "jpa-governance-" + UUID.randomUUID();
        AssistantKnowledgeJpaWriter.KnowledgeCommand command = new AssistantKnowledgeJpaWriter.KnowledgeCommand(
                slug, "en", "JPA governance", "Public thesis guidance", "academic-office", 20, null);

        AssistantKnowledgeJpaWriter.RevisionSnapshot draft = writer.create(command, "admin-a");
        assertThat(draft.state()).isEqualTo("DRAFT");
        assertThat(writer.submit(draft.documentId(), "admin-a").state()).isEqualTo("PENDING_REVIEW");
        assertThatThrownBy(() -> writer.publish(draft.documentId(), "admin-a"))
                .isInstanceOf(AssistantKnowledgeJpaWriter.KnowledgePersistenceException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_SECOND_REVIEW_REQUIRED");

        AssistantKnowledgeJpaWriter.RevisionSnapshot published = writer.publish(draft.documentId(), "admin-b");
        assertThat(published.state()).isEqualTo("PUBLISHED");
        assertThat(documents.findPublicByLocale("en")).anyMatch(row -> row.getId().equals(draft.documentId())
                && row.isActive() && "PUBLIC".equals(row.getVisibility()));
    }

    @Test
    void archiveIsOwnerIndependentButStillLocksDocument() {
        String slug = "jpa-archive-" + UUID.randomUUID();
        AssistantKnowledgeJpaWriter.RevisionSnapshot draft = writer.create(
                new AssistantKnowledgeJpaWriter.KnowledgeCommand(
                        slug, "vi", "Tieu de", "No personal data", "academic-office", 30, null), "admin-a");
        writer.archive(draft.documentId(), "admin-b");
        assertThat(documents.findById(draft.documentId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void updateRejectsSlugOwnedByAnotherDocumentWithStableConflict() {
        String firstSlug = "jpa-slug-first-" + UUID.randomUUID();
        String secondSlug = "jpa-slug-second-" + UUID.randomUUID();
        AssistantKnowledgeJpaWriter.RevisionSnapshot first = writer.create(
                new AssistantKnowledgeJpaWriter.KnowledgeCommand(
                        firstSlug, "en", "First", "Public first", "academic-office", 20, null), "admin-a");
        writer.create(new AssistantKnowledgeJpaWriter.KnowledgeCommand(
                secondSlug, "en", "Second", "Public second", "academic-office", 20, null), "admin-a");

        assertThatThrownBy(() -> writer.update(first.documentId(),
                new AssistantKnowledgeJpaWriter.KnowledgeCommand(
                        secondSlug, "en", "Changed", "Public changed", "academic-office", 20, null), "admin-a"))
                .isInstanceOf(AssistantKnowledgeJpaWriter.KnowledgePersistenceException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_SLUG_CONFLICT");
    }
}
