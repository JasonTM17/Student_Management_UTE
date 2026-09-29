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
