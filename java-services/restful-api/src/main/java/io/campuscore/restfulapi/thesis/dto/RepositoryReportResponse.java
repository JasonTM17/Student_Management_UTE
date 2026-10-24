package io.campuscore.restfulapi.thesis.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryReportResponse(
        UUID reportId,
        String title,
        String url,
        String note,
        Instant submittedAt,
        Instant updatedAt,
        String fileName,
        String fileType,
        Long fileSize,
        String topicTitle,
        String topicDescription,
        String departmentName,
        String groupStatus,
        String approvalStatus,
        String submittedByDisplayName,
        String submittedByStudentNumber,
        List<RepositorySupervisorResponse> supervisors,
        List<RepositoryMemberResponse> members) { }
