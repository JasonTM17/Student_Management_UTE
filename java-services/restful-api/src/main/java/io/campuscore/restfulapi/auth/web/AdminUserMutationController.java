package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
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
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1/users")
public class AdminUserMutationController {

    private final AdminUserMutationService users;

    public AdminUserMutationController(AdminUserMutationService users) {
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return users.list(page, limit, status, search);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> input) { return users.create(input); }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> input) { return users.update(id, input); }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        users.delete(id);
        return Map.of("message", "User deleted successfully");
    }
}
