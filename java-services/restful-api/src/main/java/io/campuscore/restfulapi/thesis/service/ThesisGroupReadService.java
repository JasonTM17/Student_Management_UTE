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
            String studentId) {
        rounds.requireExisting(roundId);
        if (canReadAll(roles)) {
            return groups.findByRoundId(roundId);
        }
        return groups.findByRoundIdAndStudentId(roundId, requireStudentId(roles, studentId));
    }

    @Transactional(readOnly = true)
    public GroupResponse get(
            UUID id,
            List<String> roles,
            String studentId) {
        GroupResponse group = canReadAll(roles)
                ? groups.findById(id)
                : groups.findByIdAndStudentId(id, requireStudentId(roles, studentId));
        if (group == null) {
            throw new DomainException(
                    HttpStatus.NOT_FOUND,
                    "THESIS_GROUP_NOT_FOUND",
                    "Thesis group not found");
        }
        return group;
    }

    private static boolean canReadAll(List<String> roles) {
        return roles.contains("ADMIN")
                || roles.contains("SUPER_ADMIN")
                || roles.contains("LECTURER");
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
