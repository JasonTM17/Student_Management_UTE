package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.LecturerMutationService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1/lecturers")
public class LecturerMutationController {

    private final LecturerMutationService lecturers;

    public LecturerMutationController(LecturerMutationService lecturers) {
        this.lecturers = lecturers;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> input) {
        return lecturers.create(input);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> input) {
        return lecturers.update(id, input);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        lecturers.delete(id);
        return Map.of("message", "Lecturer deleted successfully");
    }
}
