package io.campuscore.restfulapi.thesis.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-4 format sweep: the degraded-answer contract. "Tư vấn về DevOps" must
 * retrieve as the ADVISORY PHRASE plus the topic token — the bare syllables
 * "tư"/"vấn" substring-matched "truy vấn"/"phỏng vấn" and ranked the CSDL doc
 * #1 under quota (prod screenshot answered a DevOps question with database
 * normalization text). And a document answer must leave the composer as
 * markdown (title heading + one bullet per numbered point), never the inline
 * paragraph the panel rendered as one wall of text.
 */
class ThesisAssistantDegradedAnswerFormatTest {

    @Test
    @DisplayName("retrievalTerms collapses 'tư vấn' into the phrase and keeps the topic token")
    void retrievalTermsCollapseTuVanIntoPhrase() {
        var terms = ThesisAssistantService.retrievalTerms("Tư vấn về DevOps");
        assertTrue(terms.contains("tư vấn"), () -> "expected the phrase term, got " + terms);
        assertTrue(terms.contains("devops"), () -> "expected the topic token, got " + terms);
        assertFalse(terms.contains("tư"), () -> "bare syllable 'tư' is retrieval noise: " + terms);
        assertFalse(terms.contains("vấn"), () -> "bare syllable 'vấn' is retrieval noise: " + terms);
    }

    @Test
    @DisplayName("retrievalTerms honors the unaccented 'tu van' folded alias")
    void retrievalTermsHonorFoldedAlias() {
        var terms = ThesisAssistantService.retrievalTerms("tu van ve devops");
        assertTrue(terms.contains("tư vấn"), () -> "expected the folded alias, got " + terms);
        assertFalse(terms.contains("van"), () -> "bare folded syllable leaked: " + terms);
    }

    @Test
    @DisplayName("formatDocumentAnswer shapes numbered content into heading plus bullets")
    void formatDocumentAnswerShapesMarkdown() {
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "id-1", "specialized-devops-cicd-docker-kubernetes-vi", "vi",
                "DevOps cho sinh viên", "1. Container hoá: Dockerfile đa giai đoạn. 2. CI/CD: pipeline tối thiểu.",
                "campuscore-specialized-corpus");
        String shaped = ThesisAssistantService.formatDocumentAnswer(document, document.content());
        assertTrue(shaped.startsWith("# DevOps cho sinh viên\n\n"), shaped);
        assertTrue(shaped.contains("- **Container hoá**: Dockerfile đa giai đoạn."), shaped);
        assertTrue(shaped.contains("- **CI/CD**: pipeline tối thiểu."), shaped);
        assertFalse(shaped.contains("1. "), () -> "numbered prefix must be consumed: " + shaped);
    }

    @Test
    @DisplayName("formatDocumentAnswer passes through markdown or unsegmented content")
    void formatDocumentAnswerPassesThroughOtherContent() {
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "id-2", "slug-2", "vi", "Tiêu đề", "## Đã có cấu trúc\n\n- điểm một",
                "campuscore-specialized-corpus");
        assertTrue(ThesisAssistantService.formatDocumentAnswer(document, document.content())
                .startsWith("## Đã có cấu trúc"));
        var plain = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "id-3", "slug-3", "vi", "Tiêu đề", "Một đoạn văn không đánh số.",
                "campuscore-specialized-corpus");
        assertTrue(ThesisAssistantService.formatDocumentAnswer(plain, plain.content())
                .contains("Một đoạn văn không đánh số."));
    }
}
