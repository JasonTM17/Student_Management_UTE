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
    void simpleFactualVietnameseStaysOnRag() {
        assertFalse(AssistantDifficultyRouter.requiresSynthesis("Thời gian nộp học phí là khi nào?", List.of(document("FINANCE"))));
    }

    @Test
    void comparisonQuestionUsesSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "So sánh điều kiện đăng ký và các trường hợp ngoại lệ giúp tôi.",
                List.of(document("ACADEMIC_CATALOG"))));
    }

    @Test
    void englishComplexMarkerUsesSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "How can I register for thesis defense?",
                List.of(document("THESIS"))));
    }

    @Test
    void longQuestionWithManyWordsUsesSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "Tôi muốn biết chi tiết về toàn bộ các quy định liên quan đến việc bảo lưu kết quả học tập",
                List.of(document("ACADEMIC_CATALOG"))));
    }

    @Test
    void threeOrMoreDocumentsRequireSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "Thông tin đồ án",
                List.of(document("THESIS"), document("THESIS"), document("THESIS"))));
    }

    @Test
    void multipleDomainsRequireSynthesis() {
        assertTrue(AssistantDifficultyRouter.requiresSynthesis(
                "Tôi cần thông tin này.",
                List.of(document("ACADEMIC_CATALOG"), document("REGISTRATION"))));
    }

    @Test
    void emptyDocumentsOrNullMessageStaysOnRag() {
        assertFalse(AssistantDifficultyRouter.requiresSynthesis(null, List.of(document("THESIS"))));
        assertFalse(AssistantDifficultyRouter.requiresSynthesis("Câu hỏi bất kỳ", List.of()));
        assertFalse(AssistantDifficultyRouter.requiresSynthesis("Câu hỏi bất kỳ", null));
    }

    private static ThesisAssistantKnowledgeRepository.KnowledgeDocument document(String domain) {
        return new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "source-1", "source-1", "vi", "Title", "Content", "seed", domain,
                null, null, null, null, null, null, null, null);
    }
}
