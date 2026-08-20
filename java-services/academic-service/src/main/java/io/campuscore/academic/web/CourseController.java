package io.campuscore.academic.web;

import io.campuscore.academic.service.CourseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.CourseResponse create(@Valid @RequestBody AcademicDtos.CreateCourseRequest request) {
        return courses.create(request);
    }

    @GetMapping
    public Page<AcademicDtos.CourseResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return courses.findAll(page, limit);
    }

    @GetMapping("{id}")
    public AcademicDtos.CourseResponse findOne(@PathVariable UUID id) {
        return courses.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.CourseResponse update(@PathVariable UUID id, @Valid @RequestBody AcademicDtos.UpdateCourseRequest request) {
        return courses.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AcademicDtos.DeleteResponse> remove(@PathVariable UUID id) {
        courses.remove(id);
        return ResponseEntity.ok(new AcademicDtos.DeleteResponse("Course deleted successfully"));
    }
}
