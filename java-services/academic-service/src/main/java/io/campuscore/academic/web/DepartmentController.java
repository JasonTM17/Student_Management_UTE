package io.campuscore.academic.web;

import io.campuscore.academic.service.DepartmentService;
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
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.DepartmentResponse create(@Valid @RequestBody AcademicDtos.CreateDepartmentRequest request) {
        return departments.create(request);
    }

    @GetMapping
    public Page<AcademicDtos.DepartmentResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return departments.findAll(page, limit);
    }

    @GetMapping("{id}")
    public AcademicDtos.DepartmentResponse findOne(@PathVariable UUID id) {
        return departments.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody AcademicDtos.UpdateDepartmentRequest request) {
        return departments.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AcademicDtos.DeleteResponse> remove(@PathVariable UUID id) {
        departments.remove(id);
        return ResponseEntity.ok(new AcademicDtos.DeleteResponse("Department deleted successfully"));
    }
}
