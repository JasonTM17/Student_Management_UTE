package io.campuscore.restfulapi.thesis.model;

/**
 * Per-group facts the approval-withdrawal gate reads: the owning round's
 * status plus the downstream-work counts (reports, scores, council topics,
 * reviewer, results) that block a withdrawal once they exist.
 */
public record GroupWithdrawalFacts(
        String roundStatus,
        long reports,
        long scores,
        long councilTopics,
        long reviewers,
        long results) { }
