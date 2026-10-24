package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.model.RoundType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single decision point for governance capabilities that depend on the
 * round's level. The counter-review waiver is one: every shipped
 * undergraduate round type keeps its mandatory counter-reviewer
 * ({@link RoundType#allowsCounterReviewWaiver()} returns false), so the waive
 * path is closed. A future postgraduate round type turns it on per type; a
 * deployment can also force it on for every type with
 * {@code thesis.governance.counter-review-waiver.enabled}
 * (env {@code THESIS_GOVERNANCE_COUNTER_REVIEW_WAIVER_ENABLED}, default
 * false). Revoking a stored waiver is deliberately never gated here — a
 * waiver already in the data must always be clearable.
 */
@Component
public class ThesisGovernanceCapabilities {

    private final boolean counterReviewWaiverOverride;

    public ThesisGovernanceCapabilities(
            @Value("${thesis.governance.counter-review-waiver.enabled:false}")
            boolean counterReviewWaiverOverride) {
        this.counterReviewWaiverOverride = counterReviewWaiverOverride;
    }

    /**
     * True when a counter-review waiver may be granted in a round of the
     * given type: either the deployment override is on or the round type
     * itself permits it. Unknown or missing types answer false — the
     * undergraduate default is closed.
     */
    public boolean counterReviewWaiverAllowed(String thesisType) {
        RoundType roundType = null;
        if (thesisType != null) {
            try {
                roundType = RoundType.valueOf(thesisType.trim());
            } catch (IllegalArgumentException ignored) {
                roundType = null;
            }
        }
        return counterReviewWaiverAllowed(roundType);
    }

    public boolean counterReviewWaiverAllowed(RoundType roundType) {
        return counterReviewWaiverOverride
                || (roundType != null && roundType.allowsCounterReviewWaiver());
    }
}
