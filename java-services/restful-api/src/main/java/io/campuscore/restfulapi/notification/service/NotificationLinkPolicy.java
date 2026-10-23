package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.web.DomainException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Same-origin allowlist for notification deep links stored from admin writes.
 * A stored link is only rendered when it is a single-slash relative path with
 * no backslash and no control/whitespace characters; anything else would let a
 * notification carry a {@code javascript:}, {@code data:}, or protocol-relative
 * href into the clients.
 */
final class NotificationLinkPolicy {

    private static final Pattern SAFE = Pattern.compile("^/(?!/)[^\\s\\\\\\p{Cntrl}]*$");

    private NotificationLinkPolicy() {
    }

    static String requireSafe(String link) {
        if (link == null) {
            return null;
        }
        String trimmed = link.trim();
        if (!SAFE.matcher(trimmed).matches()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_LINK",
                    "link must be a same-origin path starting with a single /");
        }
        return trimmed;
    }
}
