package io.campuscore.restfulapi.people.web;

import io.campuscore.restfulapi.people.service.PeopleReadService;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Student and lecturer directory routes owned by the Java API. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class PeopleReadController {

    private final PeopleReadService people;

    public PeopleReadController(PeopleReadService people) {
        this.people = people;
    }

    @GetMapping("students")
    public StudentListResponse getStudents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status"));
        return people.findStudents(page, limit, status);
    }

    @GetMapping("students/{id}")
    public StudentResponse getStudent(
            @PathVariable String id,
            Authentication authentication) {
        return people.findStudent(id, authentication);
    }

    @GetMapping("lecturers")
    public LecturerListResponse getLecturers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return people.findLecturers(page, limit);
    }

    @GetMapping("lecturers/{id}")
    public LecturerResponse getLecturer(@PathVariable String id) {
        return people.findLecturer(id);
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
