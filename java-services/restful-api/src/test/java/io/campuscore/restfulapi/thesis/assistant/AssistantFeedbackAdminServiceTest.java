package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.thesis.assistant.AssistantFeedbackAdminService.FeedbackSummary;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssistantFeedbackAdminServiceTest {

    private JdbcTemplate jdbc;
    private AssistantFeedbackAdminService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:feedback_admin_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        service = new AssistantFeedbackAdminService(new NamedParameterJdbcTemplate(dataSource));

        jdbc.execute("CREATE SCHEMA assistant");
        jdbc.execute("""
                CREATE TABLE assistant.chat_message (
                    id UUID PRIMARY KEY,
                    conversation_id UUID,
                    turn_id UUID,
                    role VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
                    model VARCHAR(80),
                    degraded BOOLEAN DEFAULT FALSE,
                    reason_code VARCHAR(48),
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE assistant.chat_message_feedback (
                    message_id UUID NOT NULL,
                    owner_id VARCHAR(120) NOT NULL,
                    rating VARCHAR(4) NOT NULL,
                    reason VARCHAR(16),
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void summaryAggregatesTotalsReasonsAndPreviewsWithoutOwnerIdentity() {
        UUID turn = UUID.randomUUID();
        UUID userMessage = UUID.randomUUID();
        UUID answerMessage = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant.chat_message(id, turn_id, role, content, model, degraded, reason_code)"
                        + " VALUES (?, ?, 'USER', 'Lịch thi học kỳ 2 khi nào?', '-', FALSE, '-')",
                userMessage, turn);
        jdbc.update("INSERT INTO assistant.chat_message(id, turn_id, role, content, model, degraded, reason_code)"
                        + " VALUES (?, ?, 'ASSISTANT', 'Câu trả lời về lịch thi…', 'deepseek', FALSE, 'ANSWERED')",
                answerMessage, turn);
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant.chat_message(id, turn_id, role, content, model, degraded, reason_code)"
                        + " VALUES (?, ?, 'ASSISTANT', 'Khác', 'deepseek', TRUE, 'DEGRADED')",
                other, UUID.randomUUID());
        // Distinct updated_at pins the recency order — equal timestamps make
        // "recent first" ambiguous no matter the tiebreaker.
        jdbc.update("INSERT INTO assistant.chat_message_feedback(message_id, owner_id, rating, reason, updated_at)"
                        + " VALUES (?, 'owner-a', 'DOWN', 'INCORRECT', CURRENT_TIMESTAMP - INTERVAL '1' HOUR)", answerMessage);
        jdbc.update("INSERT INTO assistant.chat_message_feedback(message_id, owner_id, rating, reason)"
                        + " VALUES (?, 'owner-b', 'UP', 'HELPFUL')", other);

        FeedbackSummary summary = service.summary(null);

        assertThat(summary.totals().up()).isEqualTo(1);
        assertThat(summary.totals().down()).isEqualTo(1);
        assertThat(summary.totals().total()).isEqualTo(2);
        assertThat(summary.byReason())
                .extracting(AssistantFeedbackAdminService.ReasonCount::reason)
                .containsExactlyInAnyOrder("INCORRECT", "HELPFUL");
        assertThat(summary.recent()).hasSize(2);
        assertThat(summary.recent().get(0).rating()).isEqualTo("UP");
        assertThat(summary.recent().get(1).questionPreview()).isEqualTo("Lịch thi học kỳ 2 khi nào?");
        assertThat(summary.recent().get(1).answerPreview()).startsWith("Câu trả lời");
        assertThat(summary.recent().get(1).model()).isEqualTo("deepseek");
    }

    @Test
    void summaryCapsPreviewsAndHonoursTheLimit() {
        String longText = "đ".repeat(400);
        for (int i = 0; i < 3; i++) {
            UUID message = UUID.randomUUID();
            jdbc.update("INSERT INTO assistant.chat_message(id, turn_id, role, content, model, degraded, reason_code)"
                            + " VALUES (?, ?, 'ASSISTANT', ?, 'm', FALSE, 'A')",
                    message, UUID.randomUUID(), longText);
            jdbc.update("INSERT INTO assistant.chat_message_feedback(message_id, owner_id, rating, reason)"
                            + " VALUES (?, 'owner', 'DOWN', 'OUTDATED')", message);
        }

        FeedbackSummary summary = service.summary(2);

        assertThat(summary.recent()).hasSize(2);
        assertThat(summary.recent().get(0).answerPreview()).hasSize(280);
    }
}
