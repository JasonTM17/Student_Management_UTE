package io.campuscore.analytics.service;

import io.campuscore.analytics.web.AnalyticsDtos;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    public AnalyticsDtos.StudentStatistics getStudentStatistics() {
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("ACTIVE", 0L);
        byStatus.put("INACTIVE", 0L);
        byStatus.put("SUSPENDED", 0L);

        Map<String, Long> byYear = new HashMap<>();
        byYear.put("2024", 0L);
        byYear.put("2025", 0L);
        byYear.put("2026", 0L);

        return new AnalyticsDtos.StudentStatistics(0L, 0L, byStatus, byYear);
    }

    public AnalyticsDtos.CourseStatistics getCourseStatistics() {
        Map<String, Long> byDepartment = new HashMap<>();
        return new AnalyticsDtos.CourseStatistics(0L, 0L, byDepartment);
    }

    public AnalyticsDtos.EnrollmentStatistics getEnrollmentStatistics() {
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("PENDING", 0L);
        byStatus.put("CONFIRMED", 0L);
        byStatus.put("DROPPED", 0L);
        byStatus.put("COMPLETED", 0L);
        return new AnalyticsDtos.EnrollmentStatistics(0L, byStatus);
    }

    public AnalyticsDtos.FinancialStatistics getFinancialStatistics() {
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("DRAFT", 0L);
        byStatus.put("PENDING", 0L);
        byStatus.put("PAID", 0L);
        byStatus.put("OVERDUE", 0L);
        return new AnalyticsDtos.FinancialStatistics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, byStatus);
    }

    public AnalyticsDtos.DashboardOverview getDashboardOverview() {
        return new AnalyticsDtos.DashboardOverview(0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, 0L);
    }
}
