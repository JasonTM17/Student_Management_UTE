package io.campuscore.restfulapi.thesis.assistant;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Shared verdict of the Assistant RLS runtime verification.
 *
 * <p>The verdict starts fail-closed (DEGRADED) and is flipped to VERIFIED only
 * when {@link AssistantRlsRuntimeVerifier} proves the live database passes every
 * isolation check. A DEGRADED verdict never kills the boot: assistant chat
 * endpoints answer 503 ASSISTANT_RLS_UNAVAILABLE while the rest of the API keeps
 * serving traffic. The bean only exists where the dedicated runtime role is real
 * (persistence profile without the test profile), so fixtures and parity tests
 * keep their pre-degraded-mode behavior.</p>
 */
@Component
@Profile("persistence & !test")
public class AssistantRlsState {

    public enum Verdict { DEGRADED, VERIFIED }

    private volatile Verdict verdict = Verdict.DEGRADED;
    private volatile String reason = "Assistant RLS runtime has not been verified yet";
    private volatile Instant since = Instant.now();

    public boolean verified() {
        return verdict == Verdict.VERIFIED;
    }

    public Verdict verdict() {
        return verdict;
    }

    public String reason() {
        return reason;
    }

    public Instant since() {
        return since;
    }

    public void markVerified(String detail) {
        transition(Verdict.VERIFIED, detail);
    }

    public void markDegraded(String detail) {
        transition(Verdict.DEGRADED, detail);
    }

    private synchronized void transition(Verdict next, String detail) {
        if (verdict != next) {
            since = Instant.now();
        }
        verdict = next;
        reason = detail == null || detail.isBlank()
                ? "Assistant RLS runtime state changed"
                : detail;
    }
}
