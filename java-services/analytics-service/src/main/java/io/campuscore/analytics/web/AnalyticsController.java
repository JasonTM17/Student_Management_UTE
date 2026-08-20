package io.campuscore.analytics.web;

import io.campuscore.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("students")
    public AnalyticsDtos.StudentStatistics getStudentStatistics() {
        return analytics.getStudentStatistics();
    }

    @GetMapping("courses")
    public AnalyticsDtos.CourseStatistics getCourseStatistics() {
        return analytics.getCourseStatistics();
    }

    @GetMapping("enrollments")
    public AnalyticsDtos.EnrollmentStatistics getEnrollmentStatistics() {
        return analytics.getEnrollmentStatistics();
    }

    @GetMapping("financial")
    public AnalyticsDtos.FinancialStatistics getFinancialStatistics() {
        return analytics.getFinancialStatistics();
    }

    @GetMapping("dashboard")
    public AnalyticsDtos.DashboardOverview getDashboardOverview() {
        return analytics.getDashboardOverview();
    }
}
