package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicReadService;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
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
 * Feature-gated academic catalog reads. Legacy writes, enrollment, and grade
 * routes remain owned by the academic service in this wave.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1")
public class AcademicReadController {

    private final AcademicReadService academic;

    public AcademicReadController(AcademicReadService academic) {
        this.academic = academic;
    }

    @GetMapping("semesters")
    public SemesterListResponse getSemesters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findSemesters(page, limit);
    }

    @GetMapping("semesters/{id}")
    public SemesterResponse getSemester(@PathVariable String id) {
        return academic.findSemester(id);
    }

    @GetMapping("courses")
    public CourseListResponse getCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findCourses(page, limit);
    }

    @GetMapping("courses/{id}")
    public CourseResponse getCourse(@PathVariable String id) {
        return academic.findCourse(id);
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
