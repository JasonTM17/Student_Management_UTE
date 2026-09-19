package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Response records for announcements. */
public final class AnnouncementReadDtos {

    private AnnouncementReadDtos() {
    }

    public record AnnouncementResponse(
            String id,
            String title,
            String content,
            String priority,
            List<String> targetRoles,
            List<Integer> targetYears,
            boolean isGlobal,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant publishAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant expiresAt,
            String publishedBy,
            String semesterId,
            String semesterName,
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String lecturerId,
            String lecturerDisplayName,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            int version,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant archivedAt,
            String archivedBy,
            SemesterSummary semester,
            SectionSummary section,
            LecturerSummary lecturer) {
    }

    public record SemesterSummary(String name) {
    }

    public record SectionSummary(String sectionNumber, CourseSummary course) {
    }

    public record CourseSummary(String code, String name) {
    }

    public record LecturerSummary(String id, String displayName) {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record AnnouncementListResponse(List<AnnouncementResponse> data, PageMeta meta) {
    }

    public record AnnouncementHistoryResponse(
            String id,
            String announcementId,
            String action,
            String actorId,
            String actorLabel,
            String reason,
            int version,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            JsonNode before,
            JsonNode after) {
    }

    public record AnnouncementHistoryListResponse(
            List<AnnouncementHistoryResponse> data,
            PageMeta meta) {
    }

    /**
     * Safe subset of an announcement for the anonymous public news feed.
     * Field names mirror the frontend {@code AnnouncementRecord} contract so the
     * homepage can bind the same record type. Audience-scoping metadata
     * (targetRoles/targetYears/isGlobal) is intentionally absent: a public feed
     * item is global by definition and never carries internal audience filters.
     */
    public record PublicAnnouncementResponse(
            String id,
            String title,
            String content,
            String summary,
            String coverImageUrl,
            String priority,
            String categoryId,
            String slug,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant publishAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant expiresAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            String publishedBy,
            String lecturerDisplayName,
            String semesterName,
            SemesterSummary semester,
            SectionSummary section,
            LecturerSummary lecturer) {
    }

    public record PublicAnnouncementListResponse(
            List<PublicAnnouncementResponse> data,
            PageMeta meta) {
    }
}
