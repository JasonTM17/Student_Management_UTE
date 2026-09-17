package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
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
            String lecturerId) {
        rounds.requireExisting(roundId);
        if (roles.contains("ADMIN")) {
            return groups.findByRoundId(roundId);
        }
        if (roles.contains("LECTURER")) {
            // External members declare an email/phone in `contact`, so a
            // lecturer only sees the groups whose topic they supervise — the
            // same supervisor rule the report-read path applies.
            return groups.findByRoundIdAndSupervisorId(roundId, requireLecturerId(lecturerId));
        }
        return groups.findByRoundIdAndStudentId(roundId, requireStudentId(roles, studentId));
    }

    @Transactional(readOnly = true)
    public GroupResponse get(
            UUID id,
            List<String> roles,
            String studentId,
            String lecturerId) {
        GroupResponse group;
        if (roles.contains("ADMIN")) {
            group = groups.findById(id);
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
}
