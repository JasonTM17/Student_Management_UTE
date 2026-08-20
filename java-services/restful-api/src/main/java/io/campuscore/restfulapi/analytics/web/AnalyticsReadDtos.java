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
