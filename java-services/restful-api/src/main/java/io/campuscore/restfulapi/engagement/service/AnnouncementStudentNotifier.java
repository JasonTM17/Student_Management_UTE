package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.CreateNotificationCommand;
import io.campuscore.restfulapi.people.service.PeopleReadService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * R7 fan-out: every ACTIVE student gets one in-app inbox notification when an
 * announcement is created. The notice body is the announcement's first 160
 * characters and the inbox deep-links to the announcements page.
 *
 * <p>{@code REQUIRES_NEW} is the fail-soft mechanism: the batch runs in its own
 * transaction so a notification-side failure (missing table, constraint, the
 * roster query itself) rolls back only the fan-out, never the caller's
 * announcement write. The caller ({@link AnnouncementWriteService}) wraps the
 * call in try/catch so the swallowed failure still reaches the request as a
 * successful 201.
 */
@Service
@Profile("persistence")
public class AnnouncementStudentNotifier {

    static final String TITLE_PREFIX = "[Announcement] ";
    static final String LINK = "/dashboard/announcements";
    static final String TYPE = "INFO";
    /** notifications.message is VARCHAR(2000); the brief caps previews at 160. */
    static final int MESSAGE_CODE_POINTS = 160;
    /** notifications.title is VARCHAR(240); announcement titles may reach 240 themselves. */
    static final int TITLE_CODE_POINTS = 240;

    private final PeopleReadService people;
    private final NotificationWriteRepository notifications;
    private final Clock clock = Clock.systemUTC();

    public AnnouncementStudentNotifier(
            PeopleReadService people,
            NotificationWriteRepository notifications) {
        this.people = people;
        this.notifications = notifications;
    }

    /**
     * @return number of notification rows sent (not necessarily the driver
     *     per-row count, see {@code NotificationWriteRepository.createBatch})
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fanOutToActiveStudents(AnnouncementResponse announcement) {
        // Mirror the read-side audience gating (AnnouncementReadRepository):
        // a student must never be notified about an announcement they cannot
        // see. Scheduled announcements (publishAt in the future) and
        // role-scoped ones that exclude STUDENT are not visible to the inbox
        // population, so they get no fan-out.
        Instant publishAt = announcement.publishAt();
        if (publishAt != null && publishAt.isAfter(Instant.now(clock))) {
            return 0;
        }
        List<String> targetRoles = announcement.targetRoles();
        if (targetRoles != null && !targetRoles.isEmpty()
                && !targetRoles.stream().anyMatch(role -> role.equalsIgnoreCase("STUDENT"))) {
            return 0;
        }
        List<Integer> targetYears = announcement.targetYears();
        List<String> userIds = targetYears != null && !targetYears.isEmpty()
                ? people.findActiveStudentUserIdsInYears(targetYears)
                : people.findActiveStudentUserIds();
        if (userIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        String title = capCodePoints(TITLE_PREFIX + nullToEmpty(announcement.title()), TITLE_CODE_POINTS);
        // The body is sanitized HTML; capping it raw could end mid-tag
        // (an announcement opening with an inline image previewed as a literal
        // "<img src=\"data:…" fragment). Strip tags to text first.
        String message = capCodePoints(toPlainText(announcement.content()), MESSAGE_CODE_POINTS);
        List<CreateNotificationCommand> commands = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            commands.add(new CreateNotificationCommand(
                    UUID.randomUUID().toString(),
                    userId,
                    title,
                    message,
                    TYPE,
                    LINK,
                    now));
        }
        return notifications.createBatch(commands);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final java.util.regex.Pattern DECODED_TAG_RUN =
            java.util.regex.Pattern.compile("<[a-zA-Z/!][^>]*>|<[a-zA-Z/!][^>]*$");

    private static String toPlainText(String html) {
        // Jsoup's text() decodes entities (&amp; → &, &nbsp; → space) and
        // handles malformed markup a regex cannot — a ">" inside a quoted
        // attribute or an unterminated "<..." tail on legacy pre-sanitizer
        // rows would otherwise leak literal markup into the preview.
        String text = Jsoup.parse(nullToEmpty(html)).text();
        // Decoding also resurrects markup-shaped text: "&lt;img …&gt;" stored
        // as escaped text, or legacy <textarea>/<title> RCDATA bodies, decodes
        // back into a literal "<img …>" in the preview. Strip runs that start
        // like a tag ("<" + letter/'!'/'/') while keeping natural "a < b" text.
        return DECODED_TAG_RUN.matcher(text).replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String capCodePoints(String value, int cap) {
        if (value.codePointCount(0, value.length()) <= cap) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, cap));
    }
}
