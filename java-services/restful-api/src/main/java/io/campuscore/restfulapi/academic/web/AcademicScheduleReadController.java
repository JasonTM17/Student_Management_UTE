package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicScheduleReadService;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleListResponse;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated academic schedule reads. Schedule creation, update, deletion
 * and lecturer timetable shortcuts remain owned by the legacy academic service.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-schedule-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/schedules")
public class AcademicScheduleReadController {

    private final AcademicScheduleReadService academic;

    public AcademicScheduleReadController(AcademicScheduleReadService academic) {
        this.academic = academic;
    }

    @GetMapping
    public ScheduleListResponse getSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findSchedules(page, limit);
    }

    @GetMapping("{id}")
    public ScheduleResponse getSchedule(@PathVariable String id) {
        return academic.findSchedule(id);
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
}
