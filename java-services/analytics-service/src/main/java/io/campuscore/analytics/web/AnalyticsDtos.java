package io.campuscore.analytics.web;

import java.math.BigDecimal;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record StudentStatistics(
            long totalStudents,
            long activeStudents,
            Map<String, Long> studentsByStatus,
            Map<String, Long> studentsByYear) {
    }

    public record CourseStatistics(
            long totalCourses,
            long activeCourses,
            Map<String, Long> coursesByDepartment) {
    }

    public record EnrollmentStatistics(
            long totalEnrollments,
            Map<String, Long> enrollmentsByStatus) {
    }

    public record FinancialStatistics(
            BigDecimal totalRevenue,
            BigDecimal totalPending,
            BigDecimal totalOverdue,
            Map<String, Long> invoicesByStatus) {
    }

    public record DashboardOverview(
            long totalStudents,
            long totalLecturers,
            long totalCourses,
            long totalFaculties,
            long totalDepartments,
            BigDecimal totalRevenue,
            long totalEnrollments) {
    }
}
