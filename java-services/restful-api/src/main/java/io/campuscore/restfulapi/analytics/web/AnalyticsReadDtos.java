package io.campuscore.restfulapi.analytics.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the feature-gated analytics read candidate. */
public final class AnalyticsReadDtos {

    private AnalyticsReadDtos() {
    }

    public record OverviewResponse(
            long totalStudents,
            long totalLecturers,
            long totalCourses,
            long totalSections,
            long totalEnrollments,
            long totalDepartments,
            long totalFaculties,
            long totalAcademicYears,
            long totalSemesters,
            long totalClassrooms) {
    }

    public record SectionOccupancyBucket(
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            int capacity,
            long enrolledCount,
            int occupancyRate) {
    }

    public record RegistrationPressureResponse(
            long activeSemesters,
            long totalSections,
            long atCapacity,
            long nearCapacity,
            long waitlistActive,
            int averageOccupancy,
            List<RegistrationPressureSection> highestPressure,
            List<WaitlistStatusBucket> waitlistStatus) {
    }

    public record RegistrationPressureSection(
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            int capacity,
            long enrolledCount,
            long waitlistCount,
            int occupancyRate) {
    }

    public record WaitlistStatusBucket(
            String status,
            long count) {
    }

    public record TopCourseBucket(
            String courseId,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            long sectionCount,
            long totalEnrollments) {
    }

    public record StudentStatisticsResponse(
            long total,
            long active,
            long graduated,
            long suspended,
            List<StudentYearBucket> byYear) {
    }

    public record StudentYearBucket(
            int year,
            long count) {
    }

    public record EnrollmentBySemesterBucket(
            String semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            int academicYear,
            long enrollmentCount) {
    }

    public record EnrollmentTrendBucket(
            String month,
            int year,
            int monthNumber,
            Instant startDate,
            Instant endDate,
            String labelEn,
            String labelVi,
            long enrolled,
            long dropped,
            long completed,
            long net,
            long totalActivity) {
    }

    public record OperatorSummaryResponse(
            Instant generatedAt,
            long serviceCount,
            long dependencyDown,
            long highLatency,
            List<DashboardLink> dashboards) {
    }

    public record DashboardLink(
            String label,
            String url) {
    }

    public record CockpitResponse(
            Instant generatedAt,
            OverviewResponse overview,
            List<EnrollmentTrendBucket> enrollmentTrends,
            List<SectionOccupancyBucket> sectionOccupancy,
            List<GradeDistributionBucket> gradeDistribution,
            FinanceSummaryResponse finance,
            NotificationSummaryResponse notifications,
            RegistrationPressureResponse registrationPressure,
            OperatorSummaryResponse operator) {
    }

    public record FinanceSummaryResponse(
            FinanceTotals totals,
            List<InvoiceStatusBucket> invoiceStatus,
            List<PaymentStatusBucket> paymentStatus,
            List<ProviderFunnelBucket> providerFunnel) {
    }

    public record FinanceTotals(
            BigDecimal totalInvoiced,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            long pendingInvoices,
            long overdueInvoices,
            long failedPayments) {
    }

    public record RevenueAnalyticsResponse(
            BigDecimal totalInvoiced,
            BigDecimal totalPaid,
            BigDecimal pending,
            long invoiceCount,
            long paidInvoiceCount,
            long pendingInvoiceCount) {
    }

    public record AttendanceAnalyticsResponse(
            long totalRecords,
            long present,
            long absent,
            long late,
            long excused,
            int attendanceRate) {
    }

    public record InvoiceStatusBucket(
            String status,
            long count,
            BigDecimal amount) {
    }

    public record PaymentStatusBucket(
            String status,
            long count,
            BigDecimal amount) {
    }

    public record ProviderFunnelBucket(
            String provider,
            String status,
            long count,
            BigDecimal amount) {
    }

    public record GradeDistributionBucket(
            String grade,
            long count,
            int percentage) {
    }

    public record NotificationSummaryResponse(
            long total,
            long unread,
            long read,
            List<NotificationTypeBucket> byType,
            List<RecentAttentionNotification> recentAttention) {
    }

    public record NotificationTypeBucket(
            String type,
            long count) {
    }

    public record RecentAttentionNotification(
            String id,
            String title,
            String message,
            String type,
            Instant createdAt) {
    }

}
