package io.campuscore.restfulapi.academic.model;

/**
 * A section whose stored {@code enrolledCount} disagrees with the live count
 * of active enrollments.
 */
public record DriftedSection(String sectionId, int storedCount, long trueCount) { }
