package io.campuscore.restfulapi.academic.model;

/** Per-student attendance counters inside one section, roster-scoped. */
public record SectionStudentRateRow(
        String studentId,
        String studentNumber,
        String firstName,
        String lastName,
        long present,
        long late,
        long absent) {

    /** Countable records for the eligibility signal (EXCUSED excluded). */
    public long countable() {
        return present + late + absent;
    }
}
