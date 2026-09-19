package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AdminAnalyticsRepository;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.AnalyticsOverview;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates campus-wide counts for the administrator dashboard. All queries
 * are read-only; nothing here exposes personal rows, only group-level counts.
 */
@Service
@Profile("persistence")
public class AdminAnalyticsService {

    /** Semesters shown in the trend chart (chronologically ascending). */
    static final int TREND_SEMESTERS = 5;

    private final AdminAnalyticsRepository repository;

    public AdminAnalyticsService(AdminAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverview overview() {
        // The repository returns newest-first; the trend chart reads left to
        // right, so the last N semesters are reversed into ascending order.
        List<io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.SemesterTrend> trends =
                repository.semesterTrends(TREND_SEMESTERS);
        Collections.reverse(trends);
        return new AnalyticsOverview(
                repository.departmentStats(),
                trends,
                repository.facultyRanks(),
                repository.gradeDistribution(),
                repository.totals());
    }
}
