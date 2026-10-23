package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.web.DomainException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Same-origin allowlist for notification deep links stored from admin writes.
 * A stored link is only rendered when it is a single-slash relative path with
 * no backslash, no dot segments, no percent-encoded separators or dots, and no
 * Unicode separator/format/control code points; anything else would let a
 * notification carry a {@code javascript:}, {@code data:}, traversal, or
 * protocol-relative href into the clients. The shared corpus at
 * {@code src/test/resources/notification/link-corpus.json} is the contract the
 * frontend safe-path module and migration V142 mirror exactly.
 */
final class NotificationLinkPolicy {

    private static final Pattern SAFE = Pattern.compile("^/(?!/)[^\\\\\\p{C}\\p{Z}]*$");
    private static final Pattern DOT_SEGMENT = Pattern.compile("(^|/)\\.\\.?(/|$)");
    private static final Pattern ENCODED_SEPARATOR_OR_DOT =
            Pattern.compile("%(?i:2e|2f|5c|25)");

    private NotificationLinkPolicy() {
    }

    static String requireSafe(String link) {
        if (link == null) {
            return null;
        }
        // Edge whitespace rejects rather than trims: the stored link must be
        // the byte-exact path a client later navigates to.
        if (!SAFE.matcher(link).matches()
                || DOT_SEGMENT.matcher(link).find()
                || ENCODED_SEPARATOR_OR_DOT.matcher(link).find()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_LINK",
                    "link must be a same-origin path starting with a single /");
        }
        return link;
    }
}
