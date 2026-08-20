package io.campuscore.restfulapi.analytics.service;

import io.campuscore.restfulapi.analytics.repository.AnalyticsReadRepository;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceTotals;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.InvoiceStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OverviewResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.PaymentStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.ProviderFunnelBucket;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application service for the initial analytics dashboard slice. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.analytics-read", name = "enabled", havingValue = "true")
public class AnalyticsReadService {

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
}
