package io.campuscore.restfulapi.thesis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only faculty observation projection for the faculty head (TRUONG_KHOA). */
public final class ThesisDeanOverviewDtos {
    private ThesisDeanOverviewDtos() { }

    public record DeanOverviewResponse(
            FacultyScope faculty,
            RoundInfo round,
            OverviewStats stats,
            List<SupervisorWorkload> supervisors,
            List<DeanGroup> groups) { }

    /** The dean's own academic placement: department plus its parent faculty. */
    public record FacultyScope(
            String facultyId,
            String facultyName,
            String departmentId,
            String departmentName) { }

    public record RoundInfo(UUID id, String name, String thesisType, String status) { }

    public record OverviewStats(
            int totalGroups,
            int groupsPendingApproval,
            int groupsApproved,
            int groupsRejected,
            int groupsWithoutTopic,
            int totalMembers,
            int facultyTopics,
            int supervisorCount,
            List<DepartmentCount> byDepartment) { }

    public record DepartmentCount(
            String departmentId,
            String departmentName,
            int groupCount,
            int topicCount) { }

    public record SupervisorWorkload(
            String lecturerId,
            String employeeId,
            String displayName,
            String departmentName,
            int supervisedTopics,
            int supervisedGroups) { }

    public record DeanGroup(
            UUID id,
            GroupStatus status,
            ApprovalStatus approvalStatus,
            String rejectionReason,
            UUID topicId,
            String topicTitle,
            String departmentId,
            String departmentName,
            List<DeanSupervisor> supervisors,
            List<DeanMember> members,
            Instant createdAt) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeanSupervisor(
            String lecturerId,
            String employeeId,
            String displayName,
            String departmentName,
            int supervisorOrder) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeanMember(
            String studentNumber,
            String displayName,
            String contact,
            boolean isExternal,
            boolean isLeader,
            int memberOrder) { }
}
