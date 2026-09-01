package io.campuscore.restfulapi.engagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository.AuditCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.CreateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.PatchValue;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.TransitionCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.UpdateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementHistoryListResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementHistoryResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.PageMeta;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.DeleteAnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.LifecycleRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Transactional announcement mutation and audit service. */
@Service
@Profile("persistence")
public class AnnouncementWriteService {

    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> TARGET_ROLES = Set.of("STUDENT", "LECTURER", "ADMIN", "SUPER_ADMIN");
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_ACTOR_LABEL_LENGTH = 240;

    private final AnnouncementWriteRepository announcements;
    private final AnnouncementAuditRepository audits;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public AnnouncementWriteService(
            AnnouncementWriteRepository announcements,
            AnnouncementAuditRepository audits,
            ObjectMapper objectMapper) {
        this.announcements = announcements;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnnouncementResponse create(
            String publishedBy,
            String publisherLabel,
            CreateAnnouncementRequest request) {
        String publisher = requireText(publishedBy, "publishedBy");
        String actorLabel = requireActorLabel(publisherLabel, "publisherLabel");
        String priority = request.priority() == null ? "NORMAL" : request.priority();
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, NORMAL, HIGH, or URGENT");
        }
        List<String> targetRoles = immutableStrings(request.targetRoles(), "targetRoles");
        List<Integer> targetYears = immutableYears(request.targetYears());
        Instant now = Instant.now(clock);
        AnnouncementResponse created = announcements.create(new CreateAnnouncementCommand(
                UUID.randomUUID().toString(),
                requireValue(request.title(), "title"),
                requireValue(request.content(), "content"),
                priority,
                targetRoles,
                targetYears,
                Boolean.TRUE.equals(request.isGlobal()),
                request.publishAt(),
                request.expiresAt(),
                publisher,
                request.semesterId(),
                request.sectionId(),
                request.lecturerId(),
                now));
        appendAudit("CREATED", publisher, actorLabel, "Announcement created", null, created, now);
        return created;
    }

    @Transactional
    public AnnouncementResponse update(
            String actorId,
            String actorLabel,
            String announcementId,
            UpdateAnnouncementRequest request) {
        String actor = requireText(actorId, "actor");
        String label = requireActorLabel(actorLabel, "actorLabel");
        String id = requireText(announcementId, "announcement id");
        String reason = requireReason(request.reason());
        int expectedVersion = requireVersion(request.expectedVersion());
        AnnouncementResponse before = requireAnnouncement(id);
        requireActive(before);
        if (request.has("priority") && !PRIORITIES.contains(request.priority())) {
            throw new IllegalArgumentException("priority must be LOW, NORMAL, HIGH, or URGENT");
        }
        requireEditableField(request);
        Instant now = Instant.now(clock);
        int changed = announcements.update(new UpdateAnnouncementCommand(
                id,
                patch(request, "title", request.title()),
                patch(request, "content", request.content()),
                patch(request, "priority", request.priority()),
                patch(
                        request,
                        "targetRoles",
                        request.has("targetRoles") ? immutableStrings(request.targetRoles(), "targetRoles") : null),
                patch(
                        request,
                        "targetYears",
                        request.has("targetYears") ? immutableYears(request.targetYears()) : null),
                patch(request, "isGlobal", request.isGlobal()),
                patch(request, "publishAt", request.publishAt()),
                patch(request, "expiresAt", request.expiresAt()),
                patch(request, "semesterId", request.semesterId()),
                patch(request, "sectionId", request.sectionId()),
                patch(request, "lecturerId", request.lecturerId()),
                now,
                expectedVersion));
        if (changed != 1) {
            throw conflict("ANNOUNCEMENT_VERSION_CONFLICT", "Announcement was changed by another administrator");
        }
        AnnouncementResponse after = requireAnnouncement(id);
        appendAudit("UPDATED", actor, label, reason, before, after, now);
        return after;
    }

    @Transactional
    public AnnouncementResponse archive(
            String actorId,
            String actorLabel,
            String announcementId,
            LifecycleRequest request) {
        return transition(actorId, actorLabel, announcementId, request, true);
    }

    @Transactional
    public AnnouncementResponse restore(
            String actorId,
            String actorLabel,
            String announcementId,
            LifecycleRequest request) {
        return transition(actorId, actorLabel, announcementId, request, false);
    }

    @Transactional
    public DeleteAnnouncementResponse delete(String actorId, String actorLabel, String announcementId) {
        String actor = requireText(actorId, "actor");
        String label = requireActorLabel(actorLabel, "actorLabel");
        String id = requireText(announcementId, "announcement id");
        AnnouncementResponse before = requireAnnouncement(id);
        if (before.archivedAt() != null) {
            return new DeleteAnnouncementResponse("Announcement deleted successfully");
        }
        Instant now = Instant.now(clock);
        int changed = announcements.archive(new TransitionCommand(
                id,
                before.version(),
                actor,
                now,
                now));
        if (changed != 1) {
            throw conflict("ANNOUNCEMENT_VERSION_CONFLICT", "Announcement was changed by another administrator");
        }
        AnnouncementResponse after = requireAnnouncement(id);
        appendAudit(
                "ARCHIVED",
                actor,
                label,
                "Archived from the legacy delete action",
                before,
                after,
                now);
        return new DeleteAnnouncementResponse("Announcement deleted successfully");
    }

    @Transactional(readOnly = true)
    public AnnouncementHistoryListResponse history(
            String announcementId,
            int page,
            int limit) {
        String id = requireText(announcementId, "announcement id");
        requirePage(page, limit);
        requireAnnouncement(id);
        long total = audits.countByAnnouncementId(id);
        List<AnnouncementHistoryResponse> data =
                audits.findByAnnouncementId(id, offset(page, limit), limit);
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        return new AnnouncementHistoryListResponse(
                data,
                new PageMeta(total, page, limit, Math.toIntExact(totalPages)));
    }

    private AnnouncementResponse transition(
            String actorId,
            String actorLabel,
            String announcementId,
            LifecycleRequest request,
            boolean archive) {
        String actor = requireText(actorId, "actor");
        String label = requireActorLabel(actorLabel, "actorLabel");
        String id = requireText(announcementId, "announcement id");
        String reason = requireReason(request.reason());
        int expectedVersion = requireVersion(request.expectedVersion());
        AnnouncementResponse before = requireAnnouncement(id);
        if (archive && before.archivedAt() != null) {
            throw conflict("ANNOUNCEMENT_ALREADY_ARCHIVED", "Announcement is already archived");
        }
        if (!archive && before.archivedAt() == null) {
            throw conflict("ANNOUNCEMENT_NOT_ARCHIVED", "Announcement is not archived");
        }
        Instant now = Instant.now(clock);
        TransitionCommand command = new TransitionCommand(
                id,
                expectedVersion,
                archive ? actor : null,
                archive ? now : null,
                now);
        int changed = archive
                ? announcements.archive(command)
                : announcements.restore(command);
        if (changed != 1) {
            throw conflict("ANNOUNCEMENT_VERSION_CONFLICT", "Announcement was changed by another administrator");
        }
        AnnouncementResponse after = requireAnnouncement(id);
        appendAudit(archive ? "ARCHIVED" : "RESTORED", actor, label, reason, before, after, now);
        return after;
    }

    private AnnouncementResponse requireAnnouncement(String id) {
        return announcements.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
    }

    private static void requireActive(AnnouncementResponse announcement) {
        if (announcement.archivedAt() != null) {
            throw conflict("ANNOUNCEMENT_ARCHIVED", "Archived announcements must be restored before editing");
        }
    }

    private void appendAudit(
            String action,
            String actorId,
            String actorLabel,
            String reason,
            AnnouncementResponse before,
            AnnouncementResponse after,
            Instant createdAt) {
        audits.append(new AuditCommand(
                UUID.randomUUID().toString(),
                after == null ? before.id() : after.id(),
                action,
                actorId,
                actorLabel,
                reason,
                after == null ? before.version() : after.version(),
                snapshot(before),
                snapshot(after),
                createdAt));
    }

    private String snapshot(AnnouncementResponse value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Announcement audit snapshot could not be serialized", exception);
        }
    }

    private static void requireEditableField(UpdateAnnouncementRequest request) {
        boolean editable = request.presentFields().stream()
                .anyMatch(field -> !"reason".equals(field) && !"expectedVersion".equals(field));
        if (!editable) {
            throw new IllegalArgumentException("At least one editable announcement field is required");
        }
    }

    private static String requireReason(String value) {
        String reason = requireText(value, "reason").trim();
        if (reason.codePointCount(0, reason.length()) > 500) {
            throw new IllegalArgumentException("reason must contain at most 500 characters");
        }
        return reason;
    }

    private static String requireActorLabel(String value, String name) {
        String label = requireText(value, name).trim();
        if (label.codePointCount(0, label.length()) > MAX_ACTOR_LABEL_LENGTH) {
            throw new IllegalArgumentException(name + " must contain at most " + MAX_ACTOR_LABEL_LENGTH + " characters");
        }
        return label;
    }

    private static int requireVersion(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("expectedVersion must be a non-negative integer");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireValue(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static List<String> immutableStrings(List<?> values, String name) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(name + " must contain strings");
            }
            if ("targetRoles".equals(name) && !TARGET_ROLES.contains(text)) {
                throw new IllegalArgumentException(
                        "targetRoles must contain supported campus audiences");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<Integer> immutableYears(List<?> values) {
        if (values == null) {
            return List.of();
        }
        List<Integer> result = new java.util.ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Integer year) || year < 1) {
                throw new IllegalArgumentException("targetYears must be positive integers");
            }
            result.add(year);
        }
        return List.copyOf(result);
    }

    private static <T> PatchValue<T> patch(UpdateAnnouncementRequest request, String field, T value) {
        return request.has(field) ? PatchValue.present(value) : PatchValue.omitted();
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_HISTORY_PAGE_SIZE);
        }
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }
}
