package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.SupportTicketReadRepository;
import io.campuscore.restfulapi.engagement.repository.SupportTicketReadRepository.SupportTicketFilter;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.PageMeta;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketListResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only application service for engagement support-ticket strangler routes. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-read", name = "enabled", havingValue = "true")
public class SupportTicketReadService {

    public static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final SupportTicketReadRepository tickets;

    public SupportTicketReadService(SupportTicketReadRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional(readOnly = true)
    public SupportTicketListResponse findMine(String userId, int page, int limit) {
        String subject = requireUserId(userId);
        requirePage(page, limit);
        long total = tickets.countByUser(subject);
        List<SupportTicketResponse> data = tickets.findByUser(subject, offset(page, limit), limit);
        return response(data, total, page, limit);
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse findMineById(String id, String userId) {
        return tickets.findOneByUser(requireId(id), requireUserId(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
    }

    @Transactional(readOnly = true)
    public SupportTicketListResponse findAll(
            int page,
            int limit,
            String status,
            String priority,
            String category) {
        requirePage(page, limit);
        String normalizedStatus = optional(status);
        String normalizedPriority = optional(priority);
        String normalizedCategory = optional(category);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("status must be OPEN, IN_PROGRESS, RESOLVED, or CLOSED");
        }
        if (normalizedPriority != null && !PRIORITIES.contains(normalizedPriority)) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM, HIGH, or CRITICAL");
        }
        SupportTicketFilter filter = new SupportTicketFilter(normalizedStatus, normalizedPriority, normalizedCategory);
        long total = tickets.countAll(filter);
        List<SupportTicketResponse> data = tickets.findAll(filter, offset(page, limit), limit);
        return response(data, total, page, limit);
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse findById(String id) {
        return tickets.findOne(requireId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
    }

    private static SupportTicketListResponse response(
            List<SupportTicketResponse> data,
            long total,
            int page,
            int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Support-ticket result is too large");
        }
        return new SupportTicketListResponse(data, new PageMeta(total, page, limit, (int) totalPages));
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

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return id;
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user id is required");
        }
        return userId;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
