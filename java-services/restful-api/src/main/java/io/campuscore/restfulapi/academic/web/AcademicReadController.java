package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicReadService;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role-protected academic catalog query routes. */
@RestController
@Profile("persistence")
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

    @GetMapping("faculties")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FacultyListResponse getFaculties(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findFaculties(page, limit);
    }

    @GetMapping("faculties/{id}")
    public FacultyResponse getFaculty(@PathVariable String id) {
        return academic.findFaculty(id);
    }

    @GetMapping("departments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public DepartmentListResponse getDepartments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findDepartments(page, limit);
    }

    @GetMapping("departments/{id}")
    public DepartmentResponse getDepartment(@PathVariable String id) {
        return academic.findDepartment(id);
    }

    @GetMapping("academic-years")
    public AcademicYearListResponse getAcademicYears(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findAcademicYears(page, limit);
    }

    @GetMapping("academic-years/{id}")
    public AcademicYearResponse getAcademicYear(@PathVariable String id) {
        return academic.findAcademicYear(id);
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

    @GetMapping("curricula")
    public CurriculumListResponse getCurricula(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findCurricula(page, limit);
    }

    @GetMapping("curricula/{id}")
    public CurriculumResponse getCurriculum(@PathVariable String id) {
        return academic.findCurriculum(id);
    }

    @GetMapping("classrooms")
    public ClassroomListResponse getClassrooms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findClassrooms(page, limit);
    }

    @GetMapping("classrooms/{id}")
    public ClassroomResponse getClassroom(@PathVariable String id) {
        return academic.findClassroom(id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
