package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ThesisAssistantRetentionJobTest {

    @Test
    void swallowsExceptionsDuringLeaseRecovery() {
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        doThrow(new RuntimeException("database down")).when(turns).recoverExpiredLeases(any());

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class),
                turns,
                mock(AssistantCancellationRegistry.class));

        assertDoesNotThrow(job::recoverExpiredLeases);
    }

    @Test
    void swallowsExceptionsDuringPurge() {
        ThesisAssistantRepository repository = mock(ThesisAssistantRepository.class);
        doThrow(new RuntimeException("schema not found")).when(repository).purgeExpired();

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                repository,
                mock(ThesisAssistantTurnRepository.class),
                mock(AssistantCancellationRegistry.class));

        assertDoesNotThrow(job::purgeExpiredConversations);
    }
}
