package io.campuscore.analytics.service;

import io.campuscore.analytics.web.AnalyticsDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService service;

    @Test
    void getStudentStatistics_returnsValidStatistics() {
        AnalyticsDtos.StudentStatistics result = service.getStudentStatistics();
        assertThat(result).isNotNull();
    }

    @Test
    void getCourseStatistics_returnsValidStatistics() {
        AnalyticsDtos.CourseStatistics result = service.getCourseStatistics();
        assertThat(result).isNotNull();
    }

    @Test
    void getDashboardOverview_returnsValidOverview() {
        AnalyticsDtos.DashboardOverview result = service.getDashboardOverview();
        assertThat(result).isNotNull();
    }
}
