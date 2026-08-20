package io.campuscore.restfulapi.analytics.service;

import io.campuscore.restfulapi.analytics.repository.AnalyticsReadRepository;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentBySemesterBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceTotals;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.GradeDistributionBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.InvoiceStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.NotificationSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OverviewResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.PaymentStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.ProviderFunnelBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.SectionOccupancyBucket;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application service for the initial analytics dashboard slice. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.analytics-read", name = "enabled", havingValue = "true")
public class AnalyticsReadService {

    private static final List<String> LETTER_GRADES = List.of(
            "A",
            "A-",
            "B+",
            "B",
            "B-",
            "C+",
            "C",
            "C-",
            "D+",
            "D",
            "D-",
            "F");

    private final AnalyticsReadRepository analytics;

    public AnalyticsReadService(AnalyticsReadRepository analytics) {
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        return new OverviewResponse(
                analytics.countStudents(),
                analytics.countLecturers(),
                analytics.countCourses(),
                analytics.countSections(),
                analytics.countEnrollments(),
                analytics.countDepartments(),
                analytics.countFaculties(),
                analytics.countAcademicYears(),
                analytics.countSemesters(),
                analytics.countClassrooms());
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse financeSummary() {
        List<InvoiceStatusBucket> invoiceStatus = analytics.invoiceStatusBuckets();
        List<PaymentStatusBucket> paymentStatus = analytics.paymentStatusBuckets();
        List<ProviderFunnelBucket> providerFunnel = analytics.providerFunnelBuckets();

        BigDecimal totalInvoiced = invoiceStatus.stream()
                .map(InvoiceStatusBucket::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = paymentStatus.stream()
                .filter(bucket -> "COMPLETED".equals(bucket.status()))
                .map(PaymentStatusBucket::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long failedPayments = paymentStatus.stream()
                .filter(bucket -> "FAILED".equals(bucket.status()))
                .mapToLong(PaymentStatusBucket::count)
                .sum();
        long pendingInvoices = invoiceStatus.stream()
                .filter(bucket -> "PENDING".equals(bucket.status()))
                .mapToLong(InvoiceStatusBucket::count)
                .sum();
        long overdueInvoices = invoiceStatus.stream()
                .filter(bucket -> "OVERDUE".equals(bucket.status()))
                .mapToLong(InvoiceStatusBucket::count)
                .sum();

        return new FinanceSummaryResponse(
                new FinanceTotals(
                        totalInvoiced,
                        paidAmount,
                        totalInvoiced.subtract(paidAmount).max(BigDecimal.ZERO),
                        pendingInvoices,
                        overdueInvoices,
                        failedPayments),
                invoiceStatus,
                paymentStatus,
                providerFunnel);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentBySemesterBucket> enrollmentsBySemester() {
        return analytics.enrollmentsBySemester();
    }

    @Transactional(readOnly = true)
    public List<SectionOccupancyBucket> sectionOccupancy() {
        return analytics.sectionOccupancy();
    }

    @Transactional(readOnly = true)
    public List<GradeDistributionBucket> gradeDistribution() {
        Map<String, Long> counts = analytics.completedLetterGradeCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return LETTER_GRADES.stream()
                .map(grade -> bucket(grade, counts.getOrDefault(grade, 0L), total))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationSummaryResponse notificationSummary() {
        long total = analytics.countNotifications();
        long unread = analytics.countUnreadNotifications();
        return new NotificationSummaryResponse(
                total,
                unread,
                Math.max(total - unread, 0),
                analytics.notificationTypeBuckets(),
                analytics.recentAttentionNotifications());
    }

    private static GradeDistributionBucket bucket(String grade, long count, long total) {
        int percentage = total > 0 ? (int) Math.round((count / (double) total) * 100) : 0;
        return new GradeDistributionBucket(grade, count, percentage);
    }
}
