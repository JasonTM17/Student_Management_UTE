package io.campuscore.restfulapi.thesis.assistant.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.model.ExpiredLease;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantRepository;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantTurnRepository;
import io.campuscore.restfulapi.thesis.assistant.service.AssistantCancellationRegistry;
import io.campuscore.restfulapi.thesis.assistant.service.AssistantRecoveryActivity;

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

    @Test
    void idleGateSkipsTheRepositoryEntirely() {
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        AssistantRecoveryActivity activity =
                new AssistantRecoveryActivity(true, 300_000L, 900_000L, nanos::get);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        when(turns.recoverExpiredLeases(any())).thenReturn(List.of());

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class), turns,
                mock(AssistantCancellationRegistry.class), activity);

        job.recoverExpiredLeases();
        verify(turns, times(1)).recoverExpiredLeases(any());

        nanos.addAndGet(TimeUnit.SECONDS.toNanos(30));
        job.recoverExpiredLeases();
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(5));
        job.recoverExpiredLeases();
        verify(turns, times(1)).recoverExpiredLeases(any());

        nanos.addAndGet(TimeUnit.MINUTES.toNanos(10));
        job.recoverExpiredLeases();
        verify(turns, times(2)).recoverExpiredLeases(any());
    }

    @Test
    void recoveredLeasesFenceTheCancellationRegistry() {
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        AssistantRecoveryActivity activity =
                new AssistantRecoveryActivity(true, 300_000L, 900_000L, nanos::get);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        AssistantCancellationRegistry cancellations = mock(AssistantCancellationRegistry.class);
        ExpiredLease lease = new ExpiredLease("owner-a", UUID.randomUUID(), UUID.randomUUID(), 7L, "FAILED_PRE_DISPATCH");
        when(turns.recoverExpiredLeases(any())).thenAnswer(invocation -> {
            Consumer<ExpiredLease> fence = invocation.getArgument(0);
            fence.accept(lease);
            return List.of(lease);
        });

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class), turns, cancellations, activity);
        job.recoverExpiredLeases();

        verify(cancellations).fence("owner-a", lease.clientRequestId(), 7L);
    }

    @Test
    void failedSweepStaysDueOnTheNextTick() {
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        AssistantRecoveryActivity activity =
                new AssistantRecoveryActivity(true, 300_000L, 900_000L, nanos::get);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        doThrow(new RuntimeException("database down")).doReturn(List.of())
                .when(turns).recoverExpiredLeases(any());

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class), turns,
                mock(AssistantCancellationRegistry.class), activity);

        job.recoverExpiredLeases();
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(6));
        job.recoverExpiredLeases();
        verify(turns, times(2)).recoverExpiredLeases(any());
    }

    @Test
    void fullBacklogPageRetriesNextTickThenDrains() {
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        AssistantRecoveryActivity activity =
                new AssistantRecoveryActivity(true, 300_000L, 900_000L, nanos::get);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        List<ExpiredLease> fullPage = java.util.Collections.nCopies(100,
                new ExpiredLease("owner", UUID.randomUUID(), UUID.randomUUID(), 1L, "FAILED_PRE_DISPATCH"));
        when(turns.recoverExpiredLeases(any())).thenReturn(fullPage).thenReturn(List.of());

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class), turns,
                mock(AssistantCancellationRegistry.class), activity);

        job.recoverExpiredLeases();
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(30));
        job.recoverExpiredLeases();
        verify(turns, times(2)).recoverExpiredLeases(any());

        nanos.addAndGet(TimeUnit.SECONDS.toNanos(30));
        job.recoverExpiredLeases();
        verify(turns, times(2)).recoverExpiredLeases(any());
    }

    @Test
    void threeArgumentConstructorKeepsEveryTickSemantics() {
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        when(turns.recoverExpiredLeases(any())).thenReturn(List.of());

        ThesisAssistantRetentionJob job = new ThesisAssistantRetentionJob(
                mock(ThesisAssistantRepository.class), turns,
                mock(AssistantCancellationRegistry.class));

        job.recoverExpiredLeases();
        job.recoverExpiredLeases();
        verify(turns, times(2)).recoverExpiredLeases(any());
    }
}
