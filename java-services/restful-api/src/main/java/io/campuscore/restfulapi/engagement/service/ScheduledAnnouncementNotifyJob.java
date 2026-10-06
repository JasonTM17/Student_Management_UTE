package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refires the student inbox fan-out for announcements created with a future
 * {@code publishAt}. At create time {@link AnnouncementStudentNotifier}
 * correctly refuses to notify about an invisible announcement, but nothing
 * ran again when the scheduled time arrived — scheduled posts silently never
 * reached student inboxes.
 *
 * <p>Each tick picks up rows where {@code notifiedAt IS NULL} and
 * {@code publishAt <= now} (same visibility semantics as the read side:
 * archived and expired rows are excluded, expired rows are retired by
 * {@code markExpiredAsNotified}). A row whose fan-out throws keeps its NULL
 * marker and is retried on the next tick, so a transient notification outage
 * self-heals instead of being lost. Runs on the persistence profile only;
 * property {@code engagement.announcement-notify.enabled=false} disables it.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "engagement.announcement-notify", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ScheduledAnnouncementNotifyJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnnouncementNotifyJob.class);
    static final int BATCH_LIMIT = 50;

    private final AnnouncementWriteRepository announcements;
    private final AnnouncementStudentNotifier notifier;
    private final Clock clock = Clock.systemUTC();

    @Autowired
    public ScheduledAnnouncementNotifyJob(
            AnnouncementWriteRepository announcements,
            AnnouncementStudentNotifier notifier) {
        this.announcements = announcements;
        this.notifier = notifier;
    }

    @Scheduled(
            fixedDelayString = "${engagement.announcement-notify.delay-ms:300000}",
            initialDelayString = "${engagement.announcement-notify.initial-delay-ms:90000}")
    public void refireDueAnnouncements() {
        try {
            int retired = announcements.markExpiredAsNotified(Instant.now(clock));
            if (retired > 0) {
                log.info("Retired {} expired scheduled announcement(s) without fan-out", retired);
            }
            List<AnnouncementResponse> due =
                    announcements.findDueForNotification(Instant.now(clock), BATCH_LIMIT);
            for (AnnouncementResponse announcement : due) {
                try {
                    int sent = notifier.fanOutToActiveStudents(announcement);
                    announcements.markNotified(announcement.id(), Instant.now(clock));
                    log.info("Scheduled announcement {} fanned out to {} students",
                            announcement.id(), sent);
                } catch (RuntimeException exception) {
                    log.warn("Scheduled announcement {} fan-out failed; will retry next tick: {}",
                            announcement.id(), exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Scheduled announcement notify sweep failed: {}", exception.getMessage());
        }
    }
}
