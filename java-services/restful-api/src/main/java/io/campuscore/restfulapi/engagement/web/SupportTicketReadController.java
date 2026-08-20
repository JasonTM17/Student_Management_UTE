package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.service.SupportTicketReadService;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketListResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated support-ticket reads. Legacy engagement service keeps all
 * mutation ownership until writer handoff and rollback evidence are available.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketReadController {

    private final SupportTicketReadService tickets;

    public SupportTicketReadController(SupportTicketReadService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("my")
    public SupportTicketListResponse getMyTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return tickets.findMine(userId(jwt), page, limit);
    }

    @GetMapping("my/{id}")
    public SupportTicketResponse getMyTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return tickets.findMineById(id, userId(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SupportTicketListResponse getAllTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status", "priority", "category"));
        return tickets.findAll(page, limit, status, priority, category);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SupportTicketResponse getTicket(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of());
        return tickets.findById(id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }

    private static String userId(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
