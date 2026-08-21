package io.campuscore.restfulapi.analytics.web;

import io.campuscore.restfulapi.analytics.service.AnalyticsReadService;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.CockpitResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentBySemesterBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentTrendBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.FinanceSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.GradeDistributionBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.NotificationSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OperatorSummaryResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.OverviewResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RegistrationPressureResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.SectionOccupancyBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.StudentStatisticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.TopCourseBucket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated analytics reads. Revenue, attendance, lecturer dashboards,
 * metrics export and event consumers remain in the legacy
 * analytics-service for this wave.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.analytics-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/analytics")
public class AnalyticsReadController {

    private final AnalyticsReadService analytics;

    public AnalyticsReadController(AnalyticsReadService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public OverviewResponse overview(@RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.overview();
    }

    @GetMapping("finance-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'FINANCE_OFFICER')")
    public FinanceSummaryResponse financeSummary(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.financeSummary();
    }

    @GetMapping("enrollments-by-semester")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<EnrollmentBySemesterBucket> enrollmentsBySemester(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.enrollmentsBySemester();
    }

    @GetMapping("enrollment-trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<EnrollmentTrendBucket> enrollmentTrends(
            @RequestParam MultiValueMap<String, String> queryParameters,
            @RequestParam(name = "months", required = false) String months) {
        requireAllowedQuery(queryParameters, Set.of("months"));
        return analytics.enrollmentTrends(parseTrendMonths(months));
    }

    @GetMapping("operator-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public OperatorSummaryResponse operatorSummary(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.operatorSummary();
    }

    @GetMapping("cockpit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public CockpitResponse cockpit(@RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.cockpit();
    }

    @GetMapping("section-occupancy")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<SectionOccupancyBucket> sectionOccupancy(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.sectionOccupancy();
    }

    @GetMapping("registration-pressure")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public RegistrationPressureResponse registrationPressure(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.registrationPressure();
    }

    @GetMapping("top-courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<TopCourseBucket> topCourses(
            @RequestParam MultiValueMap<String, String> queryParameters,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        requireAllowedQuery(queryParameters, Set.of("limit"));
        return analytics.topCourses(limit);
    }

    @GetMapping("student-statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public StudentStatisticsResponse studentStatistics(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.studentStatistics();
    }

    @GetMapping("grade-distribution")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<GradeDistributionBucket> gradeDistribution(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.gradeDistribution();
    }

    @GetMapping("notification-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationSummaryResponse notificationSummary(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return analytics.notificationSummary();
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }

    private static int parseTrendMonths(String value) {
        if (value == null || value.isBlank()) {
            return 12;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                return 12;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return 12;
        }
    }
}
