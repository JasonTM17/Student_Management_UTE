package io.campuscore.restfulapi.analytics.service;

import io.campuscore.restfulapi.analytics.repository.AnalyticsReadRepository;
import io.campuscore.restfulapi.analytics.repository.AnalyticsReadRepository.EnrollmentTrendActivity;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.AttendanceAnalyticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.CockpitResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.DashboardLink;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentBySemesterBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentTrendBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceTotals;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.GradeDistributionBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.InvoiceStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.NotificationSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OperatorSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OverviewResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.PaymentStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.ProviderFunnelBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RegistrationPressureResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RegistrationPressureSection;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RevenueAnalyticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.SectionOccupancyBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.StudentStatisticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.TopCourseBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.WaitlistStatusBucket;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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

    private static final int DEFAULT_TREND_MONTHS = 12;
    private static final int MAX_TREND_MONTHS = 24;
    private static final int NEAR_CAPACITY_THRESHOLD = 80;
    private static final List<DashboardLink> OPERATOR_DASHBOARDS = List.of(
            new DashboardLink("Grafana", "http://127.0.0.1:3002"),
            new DashboardLink("Prometheus", "http://127.0.0.1:9090"),
            new DashboardLink("Loki", "http://127.0.0.1:3100"),
            new DashboardLink("Tempo", "http://127.0.0.1:3200"));

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
    private final Clock clock;

    public AnalyticsReadService(AnalyticsReadRepository analytics) {
        this.analytics = analytics;
        this.clock = Clock.systemUTC();
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
    public RevenueAnalyticsResponse revenueAnalytics(String semesterId) {
        return analytics.revenueAnalytics(normalizeOptional(semesterId));
    }

    @Transactional(readOnly = true)
    public AttendanceAnalyticsResponse attendanceAnalytics(String semesterId) {
        return analytics.attendanceAnalytics(normalizeOptional(semesterId));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentBySemesterBucket> enrollmentsBySemester() {
        return analytics.enrollmentsBySemester();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentTrendBucket> enrollmentTrends(int months) {
        int bucketCount = Math.min(Math.max(months, 1), MAX_TREND_MONTHS);
        YearMonth currentBucket = YearMonth.now(clock);
        YearMonth oldestBucket = currentBucket.minusMonths(bucketCount - 1L);
        Instant oldestInstant = oldestBucket.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, MutableTrendBucket> buckets = new LinkedHashMap<>();
        for (int index = 0; index < bucketCount; index++) {
            YearMonth bucket = oldestBucket.plusMonths(index);
            buckets.put(monthKey(bucket), MutableTrendBucket.from(bucket));
        }

        for (EnrollmentTrendActivity activity : analytics.enrollmentTrendActivities(oldestInstant)) {
            if (activity.enrolledAt() == null) {
                continue;
            }
            YearMonth bucket = YearMonth.from(activity.enrolledAt().atZone(ZoneOffset.UTC));
            MutableTrendBucket trendBucket = buckets.get(monthKey(bucket));
            if (trendBucket == null) {
                continue;
            }
            if ("CONFIRMED".equals(activity.status()) || "PENDING".equals(activity.status())) {
                trendBucket.enrolled++;
            } else if ("DROPPED".equals(activity.status())) {
                trendBucket.dropped++;
            } else if ("COMPLETED".equals(activity.status())) {
                trendBucket.completed++;
            }
        }

        return buckets.values().stream()
                .map(MutableTrendBucket::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OperatorSummaryResponse operatorSummary() {
        return new OperatorSummaryResponse(
                Instant.now(clock),
                8,
                0,
                0,
                OPERATOR_DASHBOARDS);
    }

    @Transactional(readOnly = true)
    public CockpitResponse cockpit() {
        return new CockpitResponse(
                Instant.now(clock),
                overview(),
                enrollmentTrends(DEFAULT_TREND_MONTHS),
                sectionOccupancy(),
                gradeDistribution(),
                financeSummary(),
                notificationSummary(),
                registrationPressure(),
                operatorSummary());
    }

    @Transactional(readOnly = true)
    public List<SectionOccupancyBucket> sectionOccupancy() {
        return analytics.sectionOccupancy();
    }

    @Transactional(readOnly = true)
    public RegistrationPressureResponse registrationPressure() {
        List<RegistrationPressureSection> sections = analytics.registrationPressureSections();
        List<WaitlistStatusBucket> waitlistStatus = analytics.waitlistStatusBuckets();
        long waitlistActive = waitlistStatus.stream()
                .filter(bucket -> "ACTIVE".equals(bucket.status()))
                .mapToLong(WaitlistStatusBucket::count)
                .sum();
        int averageOccupancy = sections.isEmpty()
                ? 0
                : (int) Math.round(sections.stream()
                        .mapToInt(RegistrationPressureSection::occupancyRate)
                        .average()
                        .orElse(0));
        List<RegistrationPressureSection> highestPressure = sections.stream()
                .sorted((left, right) -> {
                    int occupancyCompare = Integer.compare(right.occupancyRate(), left.occupancyRate());
                    if (occupancyCompare != 0) {
                        return occupancyCompare;
                    }
                    return Long.compare(right.waitlistCount(), left.waitlistCount());
                })
                .limit(8)
                .toList();

        return new RegistrationPressureResponse(
                analytics.countActiveRegistrationSemesters(),
                sections.size(),
                sections.stream().filter(section -> section.occupancyRate() >= 100).count(),
                sections.stream()
                        .filter(section -> section.occupancyRate() >= NEAR_CAPACITY_THRESHOLD
                                && section.occupancyRate() < 100)
                        .count(),
                waitlistActive,
                averageOccupancy,
                highestPressure,
                waitlistStatus);
    }

    @Transactional(readOnly = true)
    public List<TopCourseBucket> topCourses(int limit) {
        return analytics.topCourses(Math.max(limit, 0));
    }

    @Transactional(readOnly = true)
    public StudentStatisticsResponse studentStatistics() {
        return new StudentStatisticsResponse(
                analytics.countStudents(),
                analytics.countStudentsByStatus("ACTIVE"),
                analytics.countStudentsByStatus("GRADUATED"),
                analytics.countStudentsByStatus("SUSPENDED"),
                analytics.studentCountsByYear());
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

    private static String monthKey(YearMonth bucket) {
        return "%d-%02d".formatted(bucket.getYear(), bucket.getMonthValue());
    }

    private static String labelEn(YearMonth bucket) {
        return bucket.getMonth().name().substring(0, 1)
                + bucket.getMonth().name().substring(1, 3).toLowerCase()
                + " "
                + bucket.getYear();
    }

    private static String labelVi(YearMonth bucket) {
        return "tháng " + bucket.getMonthValue() + " năm " + bucket.getYear();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class MutableTrendBucket {
        private final String month;
        private final int year;
        private final int monthNumber;
        private final Instant startDate;
        private final Instant endDate;
        private final String labelEn;
        private final String labelVi;
        private long enrolled;
        private long dropped;
        private long completed;

        private MutableTrendBucket(
                String month,
                int year,
                int monthNumber,
                Instant startDate,
                Instant endDate,
                String labelEn,
                String labelVi) {
            this.month = month;
            this.year = year;
            this.monthNumber = monthNumber;
            this.startDate = startDate;
            this.endDate = endDate;
            this.labelEn = labelEn;
            this.labelVi = labelVi;
        }

        private static MutableTrendBucket from(YearMonth bucket) {
            Instant startDate = bucket.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant endDate = bucket.plusMonths(1)
                    .atDay(1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .minusMillis(1);
            return new MutableTrendBucket(
                    monthKey(bucket),
                    bucket.getYear(),
                    bucket.getMonthValue(),
                    startDate,
                    endDate,
                    labelEn(bucket),
                    labelVi(bucket));
        }

        private EnrollmentTrendBucket toResponse() {
            long net = enrolled + completed - dropped;
            long totalActivity = enrolled + completed + dropped;
            return new EnrollmentTrendBucket(
                    month,
                    year,
                    monthNumber,
                    startDate,
                    endDate,
                    labelEn,
                    labelVi,
                    enrolled,
                    dropped,
                    completed,
                    net,
                    totalActivity);
        }
    }
}
