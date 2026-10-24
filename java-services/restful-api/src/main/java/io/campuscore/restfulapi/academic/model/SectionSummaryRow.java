package io.campuscore.restfulapi.academic.model;

public record SectionSummaryRow(
        long totalSessions,
        long totalRecords,
        long present,
        long absent,
        long late,
        long excused) {
}
