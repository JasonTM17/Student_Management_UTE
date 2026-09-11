package io.campuscore.restfulapi.thesis.domain;

/**
 * Round lifecycle, ordered to match the course brief's two phases: lecturers
 * submit topics first (PROPOSAL_OPEN), the published catalog follows, then
 * student groups register, and finally results are published.
 */
public enum RoundStatus {
    DRAFT,
    PROPOSAL_OPEN,
    PROPOSALS_PUBLISHED,
    REGISTRATION_OPEN,
    REGISTRATION_CLOSED,
    RESULTS_PUBLISHED,
    CLOSED,
    CANCELLED
}
