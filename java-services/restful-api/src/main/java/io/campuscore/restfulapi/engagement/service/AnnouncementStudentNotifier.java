package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.model.CreateNotificationCommand;
import io.campuscore.restfulapi.people.service.PeopleReadService;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
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
    private final AnnouncementWriteRepository announcements;
    private final Clock clock = Clock.systemUTC();

    public AnnouncementStudentNotifier(
            PeopleReadService people,
            NotificationWriteRepository notifications,
            AnnouncementWriteRepository announcements) {
        this.people = people;
        this.notifications = notifications;
        this.announcements = announcements;
    }

    /** Called after publication commits; claim and inbox writes roll back together on failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyAfterPublication(String announcementId) {
        if (announcements.markNotified(announcementId, Instant.now(clock)) == 0) {
            return 0;
        }
        return announcements.findById(announcementId).map(this::fanOutToActiveStudents).orElse(0);
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
        Instant now = Instant.now(clock);
        Instant publishAt = announcement.publishAt();
        if ((publishAt != null && publishAt.isAfter(now))
                || (announcement.expiresAt() != null && !announcement.expiresAt().isAfter(now))
                || announcement.archivedAt() != null) {
            return 0;
        }
        List<String> targetRoles = announcement.targetRoles();
        if (!announcement.isGlobal() && (targetRoles == null || !targetRoles.contains("STUDENT"))) {
            return 0;
        }
        List<String> userIds = audienceUserIds(announcement);
        // A rescheduled announcement re-arms notifiedAt and fans out again at
        // the new publishAt; students who already carry an inbox row for this
        // announcement must not receive a second one.
        List<String> alreadyNotified = notifications.findUserIdsByAnnouncementId(announcement.id());
        if (!alreadyNotified.isEmpty()) {
            java.util.Set<String> notified = new java.util.HashSet<>(alreadyNotified);
            userIds = userIds.stream().filter(id -> !notified.contains(id)).toList();
        }
        if (userIds.isEmpty()) {
            return 0;
        }
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
                    now,
                    announcement.id()));
        }
        return notifications.createBatch(commands);
    }

    /**
     * Reconciles delivered inbox rows with the announcement's current audience
     * after an update: students who fell out of the audience lose their notice
     * (the same recall mechanism archive uses, scoped per user), students newly
     * included are notified, and students already notified are never written a
     * second row. Runs after the update commits, REQUIRES_NEW like the fan-out.
     *
     * @return {retracted, added} row counts
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] reconcileAudience(String announcementId) {
        AnnouncementResponse announcement = announcements.findById(announcementId).orElse(null);
        if (announcement == null || announcement.archivedAt() != null) {
            return new int[] {0, 0};
        }
        List<String> notified = notifications.findUserIdsByAnnouncementId(announcementId);
        List<String> audience = audienceUserIds(announcement);
        java.util.Set<String> audienceSet = new java.util.HashSet<>(audience);
        List<String> removed = notified.stream().filter(id -> !audienceSet.contains(id)).toList();
        int retracted = notifications.deleteByAnnouncementIdAndUserIds(announcementId, removed);
        int added = fanOutToActiveStudents(announcement);
        return new int[] {retracted, added};
    }

    /**
     * The announcement's current student audience — audience membership only;
     * the schedule/visibility gates stay in fanOutToActiveStudents.
     */
    private List<String> audienceUserIds(AnnouncementResponse announcement) {
        List<String> targetRoles = announcement.targetRoles();
        if (!announcement.isGlobal() && (targetRoles == null || !targetRoles.contains("STUDENT"))) {
            return List.of();
        }
        return people.findAnnouncementStudentUserIds(announcement.targetYears(), announcement.sectionId(),
                announcement.isGlobal() ? List.of() : announcement.targetRoles(), announcement.lecturerId());
    }

    /**
     * Recalls the fan-out of an archived or deleted announcement: removes every
     * inbox row this announcement delivered. Rows written before the link
     * column existed carry NULL and are deliberately left alone — nothing on
     * them identifies their origin safely. Owns its own transaction like the
     * fan-out so a recall failure can never poison the lifecycle write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recallForAnnouncement(String announcementId) {
        return notifications.deleteByAnnouncementId(announcementId);
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
