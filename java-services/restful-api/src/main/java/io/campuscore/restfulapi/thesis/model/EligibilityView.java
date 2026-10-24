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
        List<String> reasons) {
}
