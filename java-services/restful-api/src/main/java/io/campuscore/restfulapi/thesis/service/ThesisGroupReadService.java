package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.model.LecturerScope;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisGroupReadService {

    private final ThesisGroupReadRepository groups;
    private final ThesisRoundReadPort rounds;

    public ThesisGroupReadService(
            ThesisGroupReadRepository groups,
            ThesisRoundReadPort rounds) {
        this.groups = groups;
        this.rounds = rounds;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(
            UUID roundId,
            List<String> roles,
            String studentId,
            String lecturerId,
            String subject) {
        rounds.requireExisting(roundId);
        if (isFacultyAdmin(roles)) {
            return groups.findByRoundId(roundId);
        }
        // Governance readers are scoped like the dean overview: a faculty
        // head sees only their faculty, a department head only their
        // department — and member contacts (emails, declared externals) stay
        // withheld on this surface as well. A dean/department head who also
        // supervises keeps full visibility on their own supervised groups,
        // including groups outside the governance scope.
        if (roles.contains("TRUONG_KHOA") || roles.contains("TRUONG_BO_MON")) {
            LecturerScope scope = lecturerScope(roles, lecturerId, subject);
            if (scope == null) {
                return List.of();
            }
            List<GroupResponse> scoped = roles.contains("TRUONG_KHOA")
                    ? groups.findByRoundIdAndFacultyId(roundId, scope.facultyId())
                    : groups.findByRoundIdAndDepartmentId(roundId, scope.departmentId());
            List<GroupResponse> supervised = lecturerId == null || lecturerId.isBlank()
                    ? List.of()
                    : groups.findByRoundIdAndSupervisorId(roundId, lecturerId);
            return mergeScopedWithSupervised(scoped, supervised, lecturerId);
        }
        if (roles.contains("LECTURER")) {
            // External members declare an email/phone in `contact`, so a
            // lecturer only sees the groups whose topic they supervise — the
            // same supervisor rule the report-read path applies.
            return groups.findByRoundIdAndSupervisorId(roundId, requireLecturerId(lecturerId));
        }
        return groups.findByRoundIdAndStudentId(roundId, requireStudentId(roles, studentId));
    }

    /**
     * Groups the caller participates in across every round. Students see their
     * own memberships, lecturers see groups on topics they supervise — the same
     * self-scoped visibility rules as the per-round list, minus the round
     * filter. Used to land the workspace on the round that actually holds the
     * caller's group instead of the newest open round.
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> listMine(
            List<String> roles,
            String studentId,
            String lecturerId) {
        if (roles.contains("STUDENT")) {
            return groups.findByStudentId(requireStudentId(roles, studentId));
        }
        if (roles.contains("LECTURER")) {
            return groups.findBySupervisorId(requireLecturerId(lecturerId));
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public GroupResponse get(
            UUID id,
            List<String> roles,
            String studentId,
            String lecturerId,
            String subject) {
        GroupResponse group;
        if (isFacultyAdmin(roles)) {
            group = groups.findById(id);
        } else if (roles.contains("TRUONG_KHOA") || roles.contains("TRUONG_BO_MON")) {
            GroupResponse found = groups.findById(id);
            boolean supervised = isSupervisedGroup(found, lecturerId);
            LecturerScope scope = lecturerScope(roles, lecturerId, subject);
            boolean inScope = scope != null && found != null && (roles.contains("TRUONG_KHOA")
                    ? groups.groupInFaculty(id, scope.facultyId())
                    : groups.groupInDepartment(id, scope.departmentId()));
            group = (inScope || supervised) ? (supervised ? found : withoutContacts(found)) : null;
        } else if (roles.contains("LECTURER")) {
            group = supervisedGroup(id, requireLecturerId(lecturerId));
        } else {
            group = groups.findByIdAndStudentId(id, requireStudentId(roles, studentId));
        }
        if (group == null) {
            throw new DomainException(
                    HttpStatus.NOT_FOUND,
                    "THESIS_GROUP_NOT_FOUND",
                    "Thesis group not found");
        }
        return group;
    }

    /** Merge the governance-scoped groups with any group the caller supervises. */
    private List<GroupResponse> mergeScopedWithSupervised(
            List<GroupResponse> scoped, List<GroupResponse> supervised, String lecturerId) {
        Map<UUID, GroupResponse> merged = new java.util.LinkedHashMap<>();
        for (GroupResponse group : scoped) {
            merged.put(group.id(), group);
        }
        for (GroupResponse group : supervised) {
            merged.putIfAbsent(group.id(), group);
        }
        return merged.values().stream()
                .map(group -> isSupervisedGroup(group, lecturerId) ? group : withoutContacts(group))
                .toList();
    }

    private boolean isSupervisedGroup(GroupResponse group, String lecturerId) {
        return group != null && lecturerId != null && !lecturerId.isBlank()
                && group.topicId() != null
                && groups.isTopicSupervisedBy(group.topicId(), lecturerId);
    }

    /** A governance scope requires a lecturer profile bound to the caller. */
    private LecturerScope lecturerScope(
            List<String> roles, String lecturerId, String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        String id = requireLecturerId(lecturerId);
        return groups.findLecturerScope(id, subject);
    }

    /** Dean/department-head reads never carry member contacts. */
    private static GroupResponse withoutContacts(GroupResponse group) {
        if (group == null) {
            return null;
        }
        return new GroupResponse(
                group.id(),
                group.roundId(),
                group.leaderStudentId(),
                group.topicId(),
                group.status(),
                group.approvalStatus(),
                group.rejectionReason(),
                group.memberStudentIds(),
                group.members().stream()
                        .map(m -> new io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupMemberResponse(
                                m.studentId(), m.studentNumber(), m.displayName(), null,
                                m.isExternal(), m.isLeader(), m.memberOrder()))
                        .toList());
    }

    private GroupResponse supervisedGroup(UUID id, String lecturerId) {
        GroupResponse group = groups.findById(id);
        if (group == null) {
            return null;
        }
        if (group.topicId() == null
                || !groups.isTopicSupervisedBy(group.topicId(), lecturerId)) {
            // Not this lecturer's group: the member roster (with declared
            // external contacts) must not be readable at all, so the lookup
            // degrades to not-found rather than a contact-stripped view.
            return null;
        }
        return group;
    }

    private static String requireLecturerId(String lecturerId) {
        if (lecturerId == null || lecturerId.isBlank()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required");
        }
        return lecturerId;
    }

    private static String requireStudentId(List<String> roles, String studentId) {
        if (!roles.contains("STUDENT")) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_FORBIDDEN",
                    "This role cannot read thesis groups");
        }
        if (studentId == null || studentId.isBlank()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "STUDENT_PROFILE_REQUIRED",
                    "An active student profile is required");
        }
        return studentId;
    }

    private static boolean isFacultyAdmin(List<String> roles) {
        return roles.contains("ADMIN") || roles.contains("SUPER_ADMIN");
    }
}
