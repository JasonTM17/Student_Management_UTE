package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicScheduleReadRepository;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleListResponse;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only application service for the academic schedule strangler slice. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-schedule-read", name = "enabled", havingValue = "true")
public class AcademicScheduleReadService {

    public static final int MAX_PAGE_SIZE = 100;

    private final AcademicScheduleReadRepository schedules;

    public AcademicScheduleReadService(AcademicScheduleReadRepository schedules) {
        this.schedules = schedules;
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse findSchedules(int page, int limit) {
        requirePage(page, limit);
        long total = schedules.countSchedules();
        List<ScheduleResponse> data = schedules.findSchedules(offset(page, limit), limit);
        return new ScheduleListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findSchedule(String id) {
        return schedules.findScheduleById(normalizeRequired("id", id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
    }

    private static PageMeta meta(long total, int page, int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Academic schedule result is too large");
        }
        return new PageMeta(total, page, limit, (int) totalPages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static String normalizeRequired(String name, String value) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return trimmed;
    }
}
