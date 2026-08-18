package io.campuscore.engagement.service;

import io.campuscore.engagement.domain.Announcement;
import io.campuscore.engagement.repository.AnnouncementRepository;
import io.campuscore.engagement.api.EngagementDtos;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcements;

    public AnnouncementService(AnnouncementRepository announcements) {
        this.announcements = announcements;
    }

    @Transactional
    public EngagementDtos.AnnouncementResponse create(EngagementDtos.CreateAnnouncementRequest request, UUID actorId) {
        Announcement announcement = new Announcement(
                request.title(),
                request.description(),
                request.priority(),
                request.targetRoles() == null ? "{}" : request.targetRoles().toString(),
                request.targetYears() == null ? new int[0] : request.targetYears().stream().mapToInt(Integer::intValue).toArray());
        announcement.setGlobal(request.isGlobal() != null && request.isGlobal());
        announcement.setPublishAt(request.publishAt());
        announcement.setExpiresAt(request.expiresAt());
        announcement.setSemesterId(request.semesterId());
        announcement.setSectionId(request.sectionId());
        return toResponse(announcements.save(announcement));
    }

    @Transactional(readOnly = true)
    public Page<EngagementDtos.AnnouncementResponse> list(Pageable pageable) {
        return announcements.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EngagementDtos.AnnouncementResponse get(UUID id) {
        return toResponse(announcements.findById(id).orElseThrow());
    }

    private EngagementDtos.AnnouncementResponse toResponse(Announcement announcement) {
        return new EngagementDtos.AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getDescription(),
                announcement.getPriority(),
                parseRoles(announcement.getTargetRoles()),
                parseIntList(announcement.getTargetYears()),
                announcement.isGlobal(),
                announcement.getPublishAt(),
                announcement.getExpiresAt(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank() || "{}".equals(roles)) {
            return List.of();
        }
        return Arrays.stream(roles.replace("[", "").replace("]", "").replace(" ", "").split(","))
                .filter(role -> !role.isBlank())
                .toList();
    }

    private List<Integer> parseIntList(int[] values) {
        if (values == null) {
            return List.of();
        }
        return Arrays.stream(values).boxed().toList();
    }
}
