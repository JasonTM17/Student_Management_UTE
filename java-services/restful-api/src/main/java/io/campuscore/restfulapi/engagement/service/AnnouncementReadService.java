package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.AnnouncementReadRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementReadRepository.AnnouncementFilter;
import io.campuscore.restfulapi.engagement.repository.AnnouncementReadRepository.UserVisibility;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementListResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.PageMeta;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Announcement query service. */
@Service
@Profile("persistence")
public class AnnouncementReadService {

    public static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "ARCHIVED", "ALL");

    private final AnnouncementReadRepository announcements;

    public AnnouncementReadService(AnnouncementReadRepository announcements) {
        this.announcements = announcements;
    }

    @Transactional(readOnly = true)
    public AnnouncementListResponse findAll(
            int page,
            int limit,
            String semesterId,
            String sectionId,
            String priority) {
        return findAll(page, limit, semesterId, sectionId, priority, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public AnnouncementListResponse findAll(
            int page,
            int limit,
            String semesterId,
            String sectionId,
            String priority,
            String status) {
        requirePage(page, limit);
        if (priority != null && !PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, NORMAL, HIGH, or URGENT");
        }
        if (status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be ACTIVE, ARCHIVED, or ALL");
        }
        AnnouncementFilter filter = new AnnouncementFilter(semesterId, sectionId, priority, status);
        long total = announcements.countAll(filter);
        List<AnnouncementResponse> data = announcements.findAll(filter, offset(page, limit), limit);
        return response(data, total, page, limit);
    }

    @Transactional(readOnly = true)
    public AnnouncementListResponse findForUser(
            List<String> roles,
            String studentId,
            Integer studentYear,
            String lecturerId,
            int page,
            int limit) {
        requirePage(page, limit);
        UserVisibility visibility = new UserVisibility(
                roles == null ? List.of() : List.copyOf(roles),
                studentId,
                studentYear,
                lecturerId,
                Instant.now());
        long total = announcements.countForUser(visibility);
        List<AnnouncementResponse> data =
                announcements.findForUser(visibility, offset(page, limit), limit);
        return response(data, total, page, limit);
    }

    private static AnnouncementListResponse response(
            List<AnnouncementResponse> data,
            long total,
            int page,
            int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Announcement result is too large");
        }
        return new AnnouncementListResponse(
                data,
                new PageMeta(total, page, limit, (int) totalPages));
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
