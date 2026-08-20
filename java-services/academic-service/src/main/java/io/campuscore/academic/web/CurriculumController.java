package io.campuscore.academic.web;

import io.campuscore.academic.service.CurriculumService;
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
@RequestMapping("/api/v1/curricula")
public class CurriculumController {

    private final CurriculumService curricula;

    public CurriculumController(CurriculumService curricula) {
        this.curricula = curricula;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.CurriculumResponse create(@Valid @RequestBody AcademicDtos.CreateCurriculumRequest request) {
        return curricula.create(request);
    }

    @GetMapping
    public Page<AcademicDtos.CurriculumResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return curricula.findAll(page, limit);
    }

    @GetMapping("{id}")
    public AcademicDtos.CurriculumResponse findOne(@PathVariable UUID id) {
        return curricula.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public AcademicDtos.CurriculumResponse update(@PathVariable UUID id, @Valid @RequestBody AcademicDtos.UpdateCurriculumRequest request) {
        return curricula.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AcademicDtos.DeleteResponse> remove(@PathVariable UUID id) {
        curricula.remove(id);
        return ResponseEntity.ok(new AcademicDtos.DeleteResponse("Curriculum deleted successfully"));
    }
}
