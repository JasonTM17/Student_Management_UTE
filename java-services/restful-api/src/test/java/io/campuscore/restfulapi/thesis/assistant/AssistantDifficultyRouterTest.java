package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantDifficultyRouterTest {

    @Test
    void directFactLookupStaysOnRag() {
        assertFalse(AssistantDifficultyRouter.requiresSynthesis("SE401 là môn gì?", List.of(document("ACADEMIC_CATALOG"))));
    }

    @Test
    void comparisonQuestionUsesSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "So sánh điều kiện đăng ký và các trường hợp ngoại lệ giúp tôi.",
                List.of(document("ACADEMIC_CATALOG"))));
    }

    @Test
    void multipleDomainsRequireSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "Tôi cần thông tin này.",
                List.of(document("ACADEMIC_CATALOG"), document("REGISTRATION"))));
    }

    private static ThesisAssistantKnowledgeRepository.KnowledgeDocument document(String domain) {
        return new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "source-1", "source-1", "vi", "Title", "Content", "seed", domain,
                null, null, null, null, null, null, null, null);
    }
}
