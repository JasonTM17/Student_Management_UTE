package io.campuscore.restfulapi.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only registration round operations with optimistic version checks. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/admin/registration/rounds")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminRegistrationController {
    private final RegistrationService service;
    public AdminRegistrationController(RegistrationService service) { this.service = service; }

    @GetMapping
    public List<RegistrationDtos.RoundView> list() { return service.adminRounds(); }

    @PostMapping
    public RegistrationDtos.RoundView create(@Valid @RequestBody AdminRoundRequest request) {
        return service.adminCreate(request.toService());
    }

    @PutMapping("/{roundId}")
    public RegistrationDtos.RoundView update(@PathVariable String roundId, @Valid @RequestBody AdminRoundRequest request) {
        return service.adminUpdate(roundId, request.toService());
    }

    @PostMapping("/{roundId}/{action}")
    public RegistrationDtos.RoundView transition(@PathVariable String roundId, @PathVariable String action,
            @RequestBody(required = false) TransitionRequest request) {
        return service.adminTransition(roundId, action, request == null ? null : request.version());
    }

    public record AdminRoundRequest(@NotBlank String semesterId,
            @NotNull Instant registrationStart, @NotNull Instant registrationEnd,
            @NotNull Instant addDropStart, @NotNull Instant addDropEnd,
            @Min(1) @Max(60) int maxCredits, String institutionTimeZone,
            Long version,
            @Size(max = 20) List<@NotNull @Min(1) @Max(20) Integer> cohortYears) {
        RegistrationService.AdminRoundRequest toService() {
            return new RegistrationService.AdminRoundRequest(semesterId, registrationStart, registrationEnd,
                    addDropStart, addDropEnd, maxCredits <= 0 ? 28 : maxCredits,
                    institutionTimeZone == null || institutionTimeZone.isBlank() ? "Asia/Ho_Chi_Minh" : institutionTimeZone,
                    version, cohortYears == null ? List.of() : cohortYears);
        }
    }
    public record TransitionRequest(Long version) { }
}
