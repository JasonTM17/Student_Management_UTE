package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conversational tier answers greetings, thanks, identity and capability
 * openers locally instead of letting them fall through to the knowledge
 * fallback ("Mình chưa tìm thấy hướng dẫn phù hợp..."), which read as broken
 * for small talk. Every reply must be non-empty, non-degraded, and carry the
 * CONVERSATIONAL reason code; every real academic question must stay null so
 * the knowledge path still serves it.
 */
class ThesisAssistantConversationalTierTest {

    @Test
    void greetingsAreAnsweredLocallyInVietnamese() {
        ChatResponse hi = ThesisAssistantService.conversationalAnswer("hi", "vi");
        assertNotNull(hi, "bare 'hi' must be answered by the conversational tier");
        assertEquals("CONVERSATIONAL", hi.reasonCode());
        assertEquals(ThesisAssistantService.CONVERSATIONAL_MODEL, hi.model());
        assertFalse(hi.degraded());
        assertTrue(hi.answer().contains("Chào"));
        assertTrue(hi.answer().contains("thời khóa biểu"));
    }

    @Test
    void identityAndCapabilityQuestionsDescribeTheAssistant() {
        ChatResponse who = ThesisAssistantService.conversationalAnswer("Bạn là ai vậy?", "vi");
        assertNotNull(who);
        assertTrue(who.answer().contains("trợ lý AI"));

        ChatResponse capable = ThesisAssistantService.conversationalAnswer(
                "Xin chào, bạn có thể giúp gì cho tôi?", "vi");
        assertNotNull(capable, "greeting + capability opener must be intercepted");
        assertTrue(capable.answer().contains("Mình có thể giúp"));

        ChatResponse english = ThesisAssistantService.conversationalAnswer("What can you help me with?", "en");
        assertNotNull(english);
        assertTrue(english.answer().contains("I can help"));
    }

    @Test
    void thanksAndFarewellsGetShortFriendlyReplies() {
        assertNotNull(ThesisAssistantService.conversationalAnswer("Cảm ơn bạn nhé", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("thank you", "en"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("tạm biệt", "vi"));
        ChatResponse thanks = ThesisAssistantService.conversationalAnswer("ok cảm ơn", "vi");
        assertTrue(thanks.answer().contains("Không có gì"));
    }

    @Test
    void realAcademicQuestionsAreNeverIntercepted() {
        assertNull(ThesisAssistantService.conversationalAnswer("Lịch học của tôi tuần này thế nào?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("Tôi còn bao nhiêu tín chỉ để hoàn thành chương trình?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("Điểm rèn luyện của tôi hiện tại thế nào?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("học phí một tín chỉ là bao nhiêu?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("Lịch thi cuối kỳ khi nào?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer(null, "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("   ", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("Tôi muốn đăng ký đề tài khóa luận, các bước thế nào?", "vi"));
        // Audit ca-nhan Q12: the "hai" greeting token hijacked the weekday
        // word — "Thứ Hai hàng tuần tôi có môn nào..." got the self-intro.
        assertNull(ThesisAssistantService.conversationalAnswer(
                "Thứ Hai hàng tuần tôi có môn nào, học mấy giờ, ở phòng nào?", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("thứ hai tuần này có hạn nộp gì không?", "vi"));
        // Wukong round-7 A8: a conversational OPENER must not hijack the
        // real question after it — "Cảm ơn, lịch học của tôi" used to
        // answer the thanks boilerplate instead of the schedule.
        assertNull(ThesisAssistantService.conversationalAnswer("Cảm ơn, lịch học của tôi", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("Thanks, what is my schedule?", "en"));
        assertNull(ThesisAssistantService.conversationalAnswer("xin chào, tôi muốn hỏi lịch học", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("hi, điểm của tôi thế nào?", "vi"));
        // Pure thanks + politeness padding stays conversational.
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn bạn nhiều", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn anh nhé", "vi"));
        // The bare greeting still works.
        assertNotNull(ThesisAssistantService.conversationalAnswer("hai", "vi"));

        // Wukong round-8:
        // M6 — "bai" (the dropped bye transliteration) is also "bài" =
        // assignment/test paper. "bai cua toi" got whole-message coverage
        // via the filler words and returned the goodbye boilerplate.
        assertNull(ThesisAssistantService.conversationalAnswer("bai cua toi", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("bai cua minh", "vi"));
        assertNull(ThesisAssistantService.conversationalAnswer("bai cua em", "vi"));
        // L1 — emoji/symbol tails are genuine small talk, not real
        // questions: the residue strip accepts \p{S}.
        assertNotNull(ThesisAssistantService.conversationalAnswer("hi 👋", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("chào 😊", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn 🙏", "vi"));
        // Kongming F6 — English courtesy tails stay conversational.
        assertNotNull(ThesisAssistantService.conversationalAnswer("thank you for your help", "en"));

        // Wukong round-9:
        // F8 — the tier now normalizes like the other paths: NFD input and
        // invisible separators used to miss every opener and fall through
        // to the provider path.
        assertNotNull(ThesisAssistantService.conversationalAnswer(
                java.text.Normalizer.normalize("cảm ơn", java.text.Normalizer.Form.NFD), "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm​ơn", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("hi​", "vi"));
        // F9 — Vietnamese intensifiers are politeness padding, not residue.
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn rất nhiều", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn quá", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("cảm ơn vô cùng", "vi"));
        // Intensifiers attached to real content still miss the tier.
        assertNull(ThesisAssistantService.conversationalAnswer("cảm ơn, điểm của tôi quá thấp", "vi"));
    }

    @Test
    void noTrailingPunctuationEscapesTheTier() {
        assertNotNull(ThesisAssistantService.conversationalAnswer("hi!", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("xin chào.", "vi"));
        assertNotNull(ThesisAssistantService.conversationalAnswer("hello?", "en"));
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }
}
