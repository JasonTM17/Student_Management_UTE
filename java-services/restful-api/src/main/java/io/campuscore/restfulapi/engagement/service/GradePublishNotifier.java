package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.CreateNotificationCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grade-publish fan-out: every student whose enrollment was just marked
 * PUBLISHED/COMPLETED gets one inbox notification deep-linking the
 * transcript. Mirrors {@link AnnouncementStudentNotifier}: {@code REQUIRES_NEW}
 * keeps a notification-side failure from rolling back the grade publish, and
 * the caller swallows the failure after logging.
 */
@Service
@Profile("persistence")
public class GradePublishNotifier {

    static final String TITLE_PREFIX = "[Grades] ";
    static final String LINK = "/dashboard/transcript";
    static final String TYPE = "SUCCESS";
    /** notifications.message is VARCHAR(2000); keep the body compact. */
    static final int MESSAGE_CODE_POINTS = 240;
    static final int TITLE_CODE_POINTS = 240;

    private final NotificationWriteRepository notifications;
    private final Clock clock = Clock.systemUTC();

    public GradePublishNotifier(NotificationWriteRepository notifications) {
        this.notifications = notifications;
    }

    /**
     * @param userIds auth user ids resolved by the caller from the
     *     Enrollment → Student join (the caller's transaction can read the
     *     pre-publish enrollment set; this new transaction cannot)
     * @return number of notification rows written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fanOutToSection(String sectionLabel, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        String title = capCodePoints(TITLE_PREFIX + sectionLabel, TITLE_CODE_POINTS);
        String message = capCodePoints(
                "Grades for " + sectionLabel + " have been officially published. "
                        + "Open your transcript to review the final result.",
                MESSAGE_CODE_POINTS);
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

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String capCodePoints(String value, int cap) {
        if (value.codePointCount(0, value.length()) <= cap) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, cap));
    }
}
