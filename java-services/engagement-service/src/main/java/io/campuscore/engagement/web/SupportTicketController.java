package io.campuscore.engagement.web;

import io.campuscore.engagement.api.EngagementDtos;
import io.campuscore.engagement.service.SupportTicketService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketController {

    private final SupportTicketService tickets;

    public SupportTicketController(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EngagementDtos.TicketResponse create(
            @RequestBody EngagementDtos.CreateTicketRequest request,
            Authentication authentication) {
        AuthUser user = AuthUser.from(authentication);
        return tickets.create(request, user.id(), user.email(), user.displayName());
    }

    @GetMapping("/my")
    public Page<EngagementDtos.TicketResponse> myTickets(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        AuthUser user = AuthUser.from(authentication);
        return tickets.listByUser(user.id(), PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)));
    }

    @GetMapping("/my/{id}")
    public EngagementDtos.TicketResponse getMyTicket(@PathVariable UUID id, Authentication authentication) {
        try {
            return tickets.getForUser(id, AuthUser.from(authentication).id());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Page<EngagementDtos.TicketResponse> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return tickets.list(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EngagementDtos.TicketResponse assign(@PathVariable UUID id, @RequestBody EngagementDtos.AssignTicketRequest request) {
        try {
            return tickets.assign(id, request.assignedTo());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EngagementDtos.TicketResponse respond(
            @PathVariable UUID id,
            @RequestBody EngagementDtos.CreateTicketResponseRequest request,
            Authentication authentication) {
        AuthUser user = AuthUser.from(authentication);
        try {
            return tickets.addResponse(id, user.id(), user.email(), user.displayName(), request.message(), request.isInternal() != null && request.isInternal());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private record AuthUser(UUID id, String email, String displayName) {

        static AuthUser from(Authentication authentication) {
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            String email = jwt.getClaimAsString("email");
            String firstName = jwt.getClaimAsString("firstName");
            String lastName = jwt.getClaimAsString("lastName");
            String displayName = (firstName == null && lastName == null) ? email : (firstName + " " + (lastName == null ? "" : lastName)).trim();
            return new AuthUser(UUID.fromString(jwt.getSubject()), email, displayName);
        }
    }
}
