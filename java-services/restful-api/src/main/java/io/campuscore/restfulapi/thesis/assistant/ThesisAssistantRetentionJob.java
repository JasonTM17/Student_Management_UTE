package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("persistence")
public class ThesisAssistantRetentionJob {
    private final ThesisAssistantRepository repository;
    private final ThesisAssistantTurnRepository turns;
    private final AssistantCancellationRegistry cancellations;

    public ThesisAssistantRetentionJob(ThesisAssistantRepository repository,
            ThesisAssistantTurnRepository turns, AssistantCancellationRegistry cancellations) {
        this.repository = repository;
        this.turns = turns;
        this.cancellations = cancellations;
    }

    /**
     * Lease recovery is deliberately much more frequent than privacy purge:
     * a 60-90 second worker lease must not remain visible as active until the
     * daily retention window. The repository CAS/generation fence remains the
     * authority; this sweep only makes the terminal transition prompt.
     */
    @Scheduled(fixedDelayString = "${assistant.recovery-delay-ms:30000}",
            initialDelayString = "${assistant.recovery-initial-delay-ms:30000}")
    public void recoverExpiredLeases() {
        turns.recoverExpiredLeases(lease ->
                cancellations.fence(lease.ownerId(), lease.clientRequestId(), lease.leaseGeneration()));
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "UTC")
    public void purgeExpiredConversations() {
        turns.purgeExpiredConversations((owner, handle) ->
                cancellations.fence(owner, handle.clientRequestId(), handle.leaseGeneration()));
        repository.purgeExpired();
    }
}
