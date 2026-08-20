package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.CreateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.PatchValue;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.UpdateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Bounded write service for feature-gated announcement creation. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
public class AnnouncementWriteService {

    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private final AnnouncementWriteRepository announcements;
    private final Clock clock = Clock.systemUTC();

    public AnnouncementWriteService(AnnouncementWriteRepository announcements) {
        this.announcements = announcements;
    }

    public AnnouncementResponse create(String publishedBy, CreateAnnouncementRequest request) {
        String publisher = requireText(publishedBy, "publishedBy");
        String priority = request.priority() == null ? "NORMAL" : request.priority();
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, NORMAL, HIGH, or URGENT");
        }
        List<String> targetRoles = immutableStrings(request.targetRoles(), "targetRoles");
        List<Integer> targetYears = immutableYears(request.targetYears());
        Instant now = Instant.now(clock);
        return announcements.create(new CreateAnnouncementCommand(
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
    }

    @Transactional
    public AnnouncementResponse update(String announcementId, UpdateAnnouncementRequest request) {
        String id = requireText(announcementId, "announcement id");
        announcements.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        if (request.has("priority") && !PRIORITIES.contains(request.priority())) {
            throw new IllegalArgumentException("priority must be LOW, NORMAL, HIGH, or URGENT");
        }
        announcements.update(new UpdateAnnouncementCommand(
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
                Instant.now(clock)));
        return announcements.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
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
}
