package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The single definition of a topic's review outcome. Finalization, the
 * publish completeness check, the publication preview, and the student-facing
 * result rows all read the same constants and the same derivation so the
 * 5.0 pass threshold and the meaning of REVIEW_FAILED / WITHDRAWN / GRADED can
 * never drift between call sites.
 */
final class ThesisReviewOutcome {

    private ThesisReviewOutcome() { }

    /** Counter-review (GVPB) pass threshold — the only place 5.0 is defined. */
    static final BigDecimal PASS_THRESHOLD = new BigDecimal("5.0");

    /** Latest counter-review fell below the pass threshold. */
    static final String REVIEW_FAILED = "REVIEW_FAILED";
    /** Governance removed the topic from the round; never score-derived. */
    static final String WITHDRAWN = "WITHDRAWN";
    /** Defense score finalized — the only score-derived success outcome. */
    static final String GRADED = "GRADED";

    /** Topic outcomes that release the publication completeness requirement. */
    static final List<String> PUBLICATION_EXCLUDED = List.of(REVIEW_FAILED, WITHDRAWN);

    /** Latest recorded counter-review score for a topic (null when none). */
    static BigDecimal latestGvpbScore(ThesisScopeRepository scope, UUID topicId) {
        List<BigDecimal> scores = scope.findGvpbScoresNewestFirst(topicId);
        return scores.isEmpty() ? null : scores.get(0);
    }

    /** True when a stored counter-review score is below the pass threshold. */
    static boolean isFailingScore(BigDecimal score) {
        return score != null && score.compareTo(PASS_THRESHOLD) < 0;
    }
}
