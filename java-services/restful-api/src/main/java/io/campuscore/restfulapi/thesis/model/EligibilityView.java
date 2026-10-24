package io.campuscore.restfulapi.thesis.model;

import java.math.BigDecimal;
import java.util.List;

/** Per-student eligibility read model shared by enforcement and the UI. */
public record EligibilityView(
        boolean eligible,
        int earnedCredits,
        int requiredCredits,
        BigDecimal cpa,
        BigDecimal requiredCpa,
        List<String> reasons,
        /**
         * Whether the backend currently refuses mutations on this standing —
         * true only in ENFORCE mode on a covered round type. Clients show the
         * warning card in ADVISORY but must never block on
         * {@code eligible == false} alone.
         */
        boolean enforced) {
}
