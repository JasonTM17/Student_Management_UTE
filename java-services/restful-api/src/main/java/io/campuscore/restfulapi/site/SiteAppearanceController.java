package io.campuscore.restfulapi.site;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site-wide appearance (accent theme, homepage hero copy, homepage post order).
 *
 * <p>GET is anonymous by design: the homepage renders this chrome for visitors
 * who have not signed in. PUT is administrator-only. The stored payload is the
 * same JSON object the frontend editor produces; the frontend re-sanitizes on
 * every read, and this controller only enforces that it is a JSON object
 * within the size bound.
 */
@RestController
@RequestMapping(path = "/api/v1/site-appearance", produces = MediaType.APPLICATION_JSON_VALUE)
public class SiteAppearanceController {

    private final SiteAppearanceStore store;

    public SiteAppearanceController(SiteAppearanceStore store) {
        this.store = store;
    }

    @GetMapping
    public String read() {
        String payload = store.read();
        return payload == null ? "{}" : payload;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String save(@RequestBody JsonNode body, Authentication authentication) {
        if (body == null || !body.isObject()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "appearance_payload_invalid",
                    "Appearance payload must be a JSON object");
        }
        if (body.toString().length() > SiteAppearanceStore.MAX_PAYLOAD_CHARS) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "appearance_payload_too_large",
                    "Appearance payload exceeds the size limit");
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt) || jwt.getSubject() == null) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "appearance_not_authenticated",
                    "Not authenticated");
        }
        return store.write(body.toString(), jwt.getSubject());
    }
}
