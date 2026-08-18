package io.campuscore.academic.web;

import io.campuscore.academic.service.FacultyService;
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
@RequestMapping("/api/v1/faculties")
public class FacultyController {

    private final FacultyService faculties;

    public FacultyController(FacultyService faculties) {
        this.faculties = faculties;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.FacultyResponse create(@Valid @RequestBody AcademicDtos.CreateFacultyRequest request) {
        return faculties.create(request);
    }

    @GetMapping
    public Page<AcademicDtos.FacultyResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return faculties.findAll(page, limit);
    }

    @GetMapping("{id}")
    public AcademicDtos.FacultyResponse findOne(@PathVariable UUID id) {
        return faculties.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.FacultyResponse update(@PathVariable UUID id, @Valid @RequestBody AcademicDtos.UpdateFacultyRequest request) {
        return faculties.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AcademicDtos.DeleteResponse> remove(@PathVariable UUID id) {
        faculties.remove(id);
        return ResponseEntity.ok(new AcademicDtos.DeleteResponse("Faculty deleted successfully"));
    }
}
