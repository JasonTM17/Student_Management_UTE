package io.campuscore.restfulapi.thesis.model;

/**
 * Locked snapshot of a thesis group row shared by the mutation services: the
 * row is read {@code FOR UPDATE} once per mutation and every later guard in
 * the same transaction decides on this snapshot.
 */
public record GroupRow(
        java.util.UUID id,
        java.util.UUID roundId,
        String leaderStudentId,
        java.util.UUID topicId,
        GroupStatus status,
        ApprovalStatus approvalStatus) {
}
