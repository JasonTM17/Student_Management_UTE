package io.campuscore.people.web;

import io.campuscore.people.service.StudentService;
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
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService students;

    public StudentController(StudentService students) {
        this.students = students;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PeopleDtos.StudentResponse create(@Valid @RequestBody PeopleDtos.CreateStudentRequest request) {
        return students.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public Page<PeopleDtos.StudentResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String status) {
        return students.findAll(page, limit, status);
    }

    @GetMapping("{id}")
    public PeopleDtos.StudentResponse findOne(@PathVariable UUID id) {
        return students.findOne(id);
    }

    @PutMapping("{id}")
    public PeopleDtos.StudentResponse update(@PathVariable UUID id, @Valid @RequestBody PeopleDtos.UpdateStudentRequest request) {
        return students.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PeopleDtos.DeleteResponse> remove(@PathVariable UUID id) {
        students.remove(id);
        return ResponseEntity.ok(new PeopleDtos.DeleteResponse("Student deleted successfully"));
    }
}
