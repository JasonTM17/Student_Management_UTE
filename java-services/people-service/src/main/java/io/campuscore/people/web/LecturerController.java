package io.campuscore.people.web;

import io.campuscore.people.service.LecturerService;
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
@RequestMapping("/api/v1/lecturers")
public class LecturerController {

    private final LecturerService lecturers;

    public LecturerController(LecturerService lecturers) {
        this.lecturers = lecturers;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PeopleDtos.LecturerResponse create(@Valid @RequestBody PeopleDtos.CreateLecturerRequest request) {
        return lecturers.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Page<PeopleDtos.LecturerResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return lecturers.findAll(page, limit);
    }

    @GetMapping("{id}")
    public PeopleDtos.LecturerResponse findOne(@PathVariable UUID id) {
        return lecturers.findOne(id);
    }

    @PutMapping("{id}")
    public PeopleDtos.LecturerResponse update(@PathVariable UUID id, @Valid @RequestBody PeopleDtos.UpdateLecturerRequest request) {
        return lecturers.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PeopleDtos.DeleteResponse> remove(@PathVariable UUID id) {
        lecturers.remove(id);
        return ResponseEntity.ok(new PeopleDtos.DeleteResponse("Lecturer deleted successfully"));
    }
}
