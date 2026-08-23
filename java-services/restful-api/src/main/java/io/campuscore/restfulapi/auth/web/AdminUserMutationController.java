package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
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
    public Map<String, Object> create(
            @RequestBody Map<String, Object> input,
            Authentication authentication) {
        return users.create(input, isSuperAdmin(authentication));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> input,
            Authentication authentication) {
        return users.update(id, input, isSuperAdmin(authentication));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @PathVariable String id,
            Authentication authentication) {
        users.delete(id, isSuperAdmin(authentication));
        return Map.of("message", "User deleted successfully");
    }

    private static boolean isSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
