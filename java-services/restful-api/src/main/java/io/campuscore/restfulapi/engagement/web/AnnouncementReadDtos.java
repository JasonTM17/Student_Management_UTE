package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible response records for the read-only announcement candidate. */
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
}
