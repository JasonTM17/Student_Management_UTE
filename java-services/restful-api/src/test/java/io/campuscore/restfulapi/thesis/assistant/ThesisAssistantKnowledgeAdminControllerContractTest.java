package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeDocumentEntity;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeDocumentJpaRepository;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeJpaWriter;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeRevisionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ThesisAssistantKnowledgeAdminControllerContractTest {

    @Test
    void academicDomainCannotReadThesisCuratedDocuments() {
        AssistantKnowledgeJpaWriter writer = mock(AssistantKnowledgeJpaWriter.class);
        AssistantKnowledgeDocumentJpaRepository documents = mock(AssistantKnowledgeDocumentJpaRepository.class);
        AssistantKnowledgeRevisionJpaRepository revisions = mock(AssistantKnowledgeRevisionJpaRepository.class);
        ThesisAssistantKnowledgeAdminController controller =
                new ThesisAssistantKnowledgeAdminController(writer, documents, revisions);

        assertThat(controller.list("ACADEMIC", null)).isEmpty();
        verifyNoInteractions(documents, revisions);
    }

    @Test
    void thesisDomainReadsOnlyTheCuratedRepository() {
        AssistantKnowledgeJpaWriter writer = mock(AssistantKnowledgeJpaWriter.class);
        AssistantKnowledgeDocumentJpaRepository documents = mock(AssistantKnowledgeDocumentJpaRepository.class);
        AssistantKnowledgeRevisionJpaRepository revisions = mock(AssistantKnowledgeRevisionJpaRepository.class);
        AssistantKnowledgeDocumentEntity document = AssistantKnowledgeDocumentEntity.draft(
                UUID.randomUUID(), "thesis-source", "en", "Thesis source", "Grounded content",
                "academic-office", 10, Instant.parse("2026-08-26T00:00:00Z"));
        when(documents.findAll(any(Sort.class))).thenReturn(List.of(document));
        ThesisAssistantKnowledgeAdminController controller =
                new ThesisAssistantKnowledgeAdminController(writer, documents, revisions);

        assertThat(controller.list("THESIS", null)).singleElement()
                .extracting(ThesisAssistantKnowledgeAdminController.KnowledgeDocumentView::slug)
                .isEqualTo("thesis-source");
    }
}
