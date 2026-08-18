package io.campuscore.thesis.service;

import io.campuscore.thesis.domain.CouncilMemberRole;
import io.campuscore.thesis.domain.CouncilStatus;
import io.campuscore.thesis.domain.ThesisCouncilMember;
import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import io.campuscore.thesis.repository.ThesisCouncilMemberRepository;
import io.campuscore.thesis.repository.ThesisDefenseCouncilRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.AddCouncilMemberRequest;
import io.campuscore.thesis.web.ThesisDtos.CouncilResponse;
import io.campuscore.thesis.web.ThesisDtos.CreateCouncilRequest;
import io.campuscore.thesis.web.ThesisDtos.ScheduleCouncilRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThesisCouncilService {

    private final ThesisDefenseCouncilRepository councils;
    private final ThesisCouncilMemberRepository members;
    private final ThesisRoundService rounds;

    public ThesisCouncilService(
            ThesisDefenseCouncilRepository councils,
            ThesisCouncilMemberRepository members,
            ThesisRoundService rounds) {
        this.councils = councils;
        this.members = members;
        this.rounds = rounds;
    }

    @Transactional
    public CouncilResponse create(CreateCouncilRequest request) {
        rounds.get(request.roundId());
        return response(councils.save(new ThesisDefenseCouncil(
                request.roundId(),
                request.departmentId())));
    }

    @Transactional(readOnly = true)
    public List<CouncilResponse> list(UUID roundId) {
        rounds.get(roundId);
        return councils.findAllByRoundIdOrderByScheduledAtAsc(roundId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public CouncilResponse addMember(UUID councilId, AddCouncilMemberRequest request) {
        ThesisDefenseCouncil council = lockedCouncil(councilId);
        if (council.getStatus() != CouncilStatus.DRAFT) {
            throw new DomainExceptions.Conflict("Only draft councils can change membership");
        }
        if (request.memberOrder() < 1 || request.memberOrder() > 5) {
            throw new DomainExceptions.Conflict("Council member order must be between 1 and 5");
        }
        List<ThesisCouncilMember> current = members.findAllByCouncilIdForUpdate(councilId);
        if (current.size() >= 5) {
            throw new DomainExceptions.Conflict("A council can contain at most 5 members");
        }
        if (current.stream().anyMatch(member -> member.getLecturerId().equals(request.lecturerId()))) {
            throw new DomainExceptions.Conflict("Lecturer is already assigned to this council");
        }
        if (current.stream().anyMatch(member -> member.getMemberOrder() == request.memberOrder())) {
            throw new DomainExceptions.Conflict("Council member order is already used");
        }
        if (request.memberRole() == CouncilMemberRole.CHAIR
                && current.stream().anyMatch(member -> member.getMemberRole() == CouncilMemberRole.CHAIR)) {
            throw new DomainExceptions.Conflict("A council can have only one chair");
        }
        if (request.memberRole() == CouncilMemberRole.SECRETARY
                && current.stream().anyMatch(member -> member.getMemberRole() == CouncilMemberRole.SECRETARY)) {
            throw new DomainExceptions.Conflict("A council can have only one secretary");
        }
        members.save(new ThesisCouncilMember(
                councilId,
                request.lecturerId(),
                request.memberRole(),
                request.memberOrder()));
        return response(council);
    }

    @Transactional
    public CouncilResponse schedule(UUID councilId, ScheduleCouncilRequest request) {
        ThesisDefenseCouncil council = lockedCouncil(councilId);
        List<ThesisCouncilMember> current = members.findAllByCouncilIdForUpdate(councilId);
        if (current.size() < 3 || current.size() > 5) {
            throw new DomainExceptions.Conflict("A council must contain between 3 and 5 members before scheduling");
        }
        if (current.stream().noneMatch(member -> member.getMemberRole() == CouncilMemberRole.CHAIR)
                || current.stream().noneMatch(member -> member.getMemberRole() == CouncilMemberRole.SECRETARY)) {
            throw new DomainExceptions.Conflict("A scheduled council needs one chair and one secretary");
        }
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new DomainExceptions.Conflict("Defense time must be in the future");
        }
        council.schedule(request.scheduledAt(), request.room().trim());
        return response(councils.save(council));
    }

    @Transactional
    public CouncilResponse openScoring(UUID councilId) {
        ThesisDefenseCouncil council = lockedCouncil(councilId);
        council.openScoring();
        return response(councils.save(council));
    }

    @Transactional
    public void finalizeCouncil(UUID councilId) {
        ThesisDefenseCouncil council = lockedCouncil(councilId);
        council.finalizeCouncil();
        councils.save(council);
    }

    @Transactional(readOnly = true)
    public ThesisDefenseCouncil get(UUID councilId) {
        return councils.findById(councilId)
                .orElseThrow(() -> new DomainExceptions.NotFound("Defense council not found"));
    }

    @Transactional(readOnly = true)
    public List<ThesisCouncilMember> getMembers(UUID councilId) {
        return members.findAllByCouncilIdOrderByMemberOrder(councilId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID councilId, UUID lecturerId) {
        return members.existsByCouncilIdAndLecturerId(councilId, lecturerId);
    }

    private ThesisDefenseCouncil lockedCouncil(UUID councilId) {
        ThesisDefenseCouncil council = councils.findByIdForUpdate(councilId);
        if (council == null) {
            throw new DomainExceptions.NotFound("Defense council not found");
        }
        return council;
    }

    private CouncilResponse response(ThesisDefenseCouncil council) {
        return CouncilResponse.from(council, members.findAllByCouncilIdOrderByMemberOrder(council.getId()));
    }
}
