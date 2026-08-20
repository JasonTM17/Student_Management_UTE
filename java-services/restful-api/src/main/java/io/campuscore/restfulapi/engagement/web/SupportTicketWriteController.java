package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.service.SupportTicketWriteService;
import io.campuscore.restfulapi.engagement.service.SupportTicketWriteService.CurrentTicketUser;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.CreateSupportTicketRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Feature-gated support-ticket creation candidate for the Java monolith. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketWriteController {

    private final SupportTicketWriteService tickets;

    public SupportTicketWriteController(SupportTicketWriteService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return tickets.create(user(jwt), request);
    }

    private static CurrentTicketUser user(Jwt jwt) {
        String id = stringClaim(jwt, "sub");
        String email = stringClaim(jwt, "email");
        if (id == null || email == null) {
            throw new BadCredentialsException("Invalid JWT claims");
        }
        String name = String.join(" ", nonBlank(stringClaim(jwt, "firstName")), nonBlank(stringClaim(jwt, "lastName"))).trim();
        return new CurrentTicketUser(id, email, name.isEmpty() ? email : name);
    }

    private static String stringClaim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new BadCredentialsException("Invalid " + claimName + " claim");
        }
        return text.isBlank() ? null : text;
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value;
    }
}
