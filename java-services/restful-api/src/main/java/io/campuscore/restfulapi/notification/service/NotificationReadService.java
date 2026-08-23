package io.campuscore.restfulapi.notification.service;

import io.campuscore.restfulapi.notification.repository.NotificationReadRepository;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationListResponse;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.PageMeta;
import io.campuscore.restfulapi.notification.web.NotificationReadDtos.UnreadCountResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Notification inbox query service. */
@Service
@Profile("persistence")
public class NotificationReadService {

    /** A bounded limit prevents an accidental unbounded inbox read. */
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationReadRepository notifications;

    public NotificationReadService(NotificationReadRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse findMy(
            String userId,
            int page,
            int limit,
            String isReadQuery) {
        requireSubject(userId);
        requirePage(page, limit);
        Boolean isRead = parseIsRead(isReadQuery);
        long offset = (long) (page - 1) * limit;
        long total = notifications.countMy(userId, isRead);
        List<NotificationResponse> data = notifications.findMy(userId, offset, limit, isRead);
        return new NotificationListResponse(
                data,
                new PageMeta(total, page, limit, totalPages(total, limit)));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String userId) {
        requireSubject(userId);
        return new UnreadCountResponse(notifications.countUnread(userId));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse findAll(
            int page,
            int limit,
            String userId) {
        requirePage(page, limit);
        String userFilter = normalizeOptionalText(userId);
        long offset = (long) (page - 1) * limit;
        long total = notifications.countAll(userFilter);
        List<NotificationResponse> data = notifications.findAll(offset, limit, userFilter);
        return new NotificationListResponse(
                data,
                new PageMeta(total, page, limit, totalPages(total, limit)));
    }

    @Transactional(readOnly = true)
    public NotificationResponse findOne(String id) {
        requireText(id, "notification id");
        return notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    /**
     * Matches the current Nest controller's strict source semantics: only the
     * exact string "true" means true; any other supplied value means false.
     */
    static Boolean parseIsRead(String value) {
        return value == null ? null : Boolean.valueOf("true".equals(value));
    }

    private static void requireSubject(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated subject is required");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int totalPages(long total, int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Notification result is too large");
        }
        return (int) totalPages;
    }
}
