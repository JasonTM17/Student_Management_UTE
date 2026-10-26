package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicSupervisorEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import io.campuscore.restfulapi.thesis.scope.ThesisDepartmentScope;

@Service
@Profile("persistence")
public class ThesisTopicService {

    private final ThesisTopicRepository topics;
    private final ThesisRoundReadPort rounds;
    private final ThesisScopeRepository scope;

    public ThesisTopicService(ThesisTopicRepository topics, ThesisRoundReadPort rounds,
            ThesisScopeRepository scope) {
        this.topics = topics;
        this.rounds = rounds;
        this.scope = scope;
    }

    /**
     * Non-published topics are only visible to their owner (and admins):
     * students never receive DRAFT/ARCHIVED listings, and a lecturer only
     * sees their own proposals, never another supervisor's drafts.
     */
    @Transactional(readOnly = true)
    public List<TopicResponse> list(UUID roundId, TopicStatus status, Jwt actor) {
        rounds.requireExisting(roundId);
        TopicStatus requestedStatus = status == null ? TopicStatus.PUBLISHED : status;
        List<ThesisTopic> result;
        if (requestedStatus == TopicStatus.PUBLISHED || requestedStatus == TopicStatus.APPROVED
                || isAdmin(actor) || ThesisDeanScope.isFacultyHead(actor)) {
            if (requestedStatus == TopicStatus.PUBLISHED) {
                List<ThesisTopic> published = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, TopicStatus.PUBLISHED);
                List<ThesisTopic> approved = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, TopicStatus.APPROVED);
                result = new java.util.ArrayList<>(published);
                result.addAll(approved);
                result.sort(java.util.Comparator.comparing(ThesisTopic::getTitle));
            } else {
                result = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus);
            }
        } else if (ThesisDepartmentScope.isDepartmentHead(actor)) {
            result = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus);
        } else if (!isLecturer(actor)) {
            result = List.of();
        } else {
            String actorId = subject(actor);
            result = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus)
                    .stream()
                    .filter(topic -> topic.getCreatedBy().equals(actorId))
                    .toList();
        }
        // Faculty and department governance queues follow the caller's live active profile.
        java.util.Set<String> deanFacultyDepts = java.util.Set.of();
        java.util.Set<UUID> deanSupervisedIds = java.util.Set.of();
        if (!isAdmin(actor) && ThesisDeanScope.isFacultyHead(actor)) {
            String facultyId = ThesisDeanScope.facultyId(scope, actor);
            deanFacultyDepts = facultyId == null ? java.util.Set.of() : new java.util.HashSet<>(
                    scope.findDepartmentIdsInFaculty(facultyId));
            String deanLecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
            deanSupervisedIds = deanLecturerId.isEmpty() || result.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(
                    scope.findSupervisedTopicIds(deanLecturerId,
                            result.stream().map(ThesisTopic::getId).toList()));
            // A dean named as a topic's counter-reviewer must reach it through
            // the same catalog a plain lecturer sees: the review assignment is
            // the access grant, the same escape ThesisReportService applies to
            // the report read. Without it the dean's review list silently
            // hides foreign-faculty assignments and they can never score.
            java.util.Set<UUID> reviewedByDean = deanLecturerId.isEmpty() || result.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(
                    scope.findReviewAssignedTopicIds(deanLecturerId,
                            result.stream().map(ThesisTopic::getId).toList()));
            java.util.Set<String> inFacultyDepts = deanFacultyDepts;
            java.util.Set<UUID> supervisedByDean = deanSupervisedIds;
            result = result.stream()
                    .filter(topic -> inFacultyDepts.contains(topic.getDepartmentId())
                            || isOwner(topic, actor)
                            || supervisedByDean.contains(topic.getId())
                            || reviewedByDean.contains(topic.getId()))
                    .toList();
        } else if (!isAdmin(actor) && ThesisDepartmentScope.isDepartmentHead(actor)) {
            String departmentId = ThesisDepartmentScope.departmentId(scope, actor);
            result = result.stream().filter(topic -> topic.getDepartmentId().equals(departmentId)).toList();
        }
        // A withdrawn topic's outcome is masked for students, so listing it
        // would offer a pick the assign path always refuses. Governance roles
        // and lecturers keep full visibility (owners/supervisors/graders must
        // still see it); students see only live topics.
        if (!isAdmin(actor) && !ThesisDeanScope.isFacultyHead(actor)
                && !ThesisDepartmentScope.isDepartmentHead(actor) && !isLecturer(actor)
                && !result.isEmpty()) {
            result = result.stream()
                    .filter(topic -> !"WITHDRAWN".equals(topic.getResultStatus()))
                    .toList();
        }
        boolean published = isRoundResultsPublished(roundId);
        // One query for every topic the caller grades as a council member, so
        // the mask check below does not turn into per-topic round-trips.
        java.util.Set<UUID> gradingTopicIds = new java.util.HashSet<>();
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!published && !isAdmin(actor) && !lecturerId.isEmpty() && !result.isEmpty()) {
            gradingTopicIds.addAll(scope.findGradingCouncilTopicIds(lecturerId,
                    result.stream().map(ThesisTopic::getId).toList()));
        }
        // After publication a student still reads only the outcome of the
        // topic their approved group took part in; the rest stay masked.
        boolean studentScoped = isStudentScoped(actor);
        java.util.Set<UUID> participatedIds = java.util.Set.of();
        String callerStudentId = normalize(actor == null ? null : actor.getClaimAsString("studentId"));
        if (published && studentScoped && !callerStudentId.isEmpty() && !result.isEmpty()) {
            participatedIds = new java.util.HashSet<>(scope.findParticipatedTopicIds(callerStudentId,
                    result.stream().map(ThesisTopic::getId).toList()));
        }
        boolean adminCaller = isAdmin(actor);
        java.util.Set<String> inFacultyDepts = deanFacultyDepts;
        java.util.Set<UUID> supervisedByDean = deanSupervisedIds;
        String callerLecturerId = lecturerId;
        // One batched read resolves every listed topic's supervisor roster, so
        // the catalog never pays a per-topic request tax. Entries carry names
        // only; the per-topic endpoint still owns the staff-only email column.
        java.util.Map<UUID, List<TopicSupervisorEntry>> supervisorsByTopic = result.isEmpty()
                ? java.util.Map.of()
                : scope.findSupervisorsByTopicIds(result.stream().map(ThesisTopic::getId).toList());
        java.util.Set<UUID> participated = participatedIds;
        return result.stream()
                .map(topic -> TopicResponse.from(topic,
                        supervisorsByTopic.getOrDefault(topic.getId(), List.of())))
                .map(topic -> maskScoreIfNeeded(topic, published,
                        adminCaller || inFacultyDepts.contains(topic.departmentId())
                                || supervisedByDean.contains(topic.id())
                                || gradingTopicIds.contains(topic.id()),
                        callerLecturerId,
                        !studentScoped || participated.contains(topic.id())))
                .toList();
    }

    /**
     * A hidden (non-published) topic responds 404 to callers who are neither
     * its owner nor an admin, so ids cannot be used to enumerate drafts.
     */
    @Transactional(readOnly = true)
    public TopicResponse get(UUID id, Jwt actor) {
        ThesisTopic topic = topics.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Thesis topic not found"));
        boolean deanInScope = ThesisDeanScope.canAccessTopic(scope, actor, topic.getId(), topic.getDepartmentId());
        if (topic.getStatus() != TopicStatus.PUBLISHED && topic.getStatus() != TopicStatus.APPROVED
                && !isAdmin(actor) && !deanInScope && !isOwner(topic, actor)
                && !ThesisDepartmentScope.owns(scope, actor, topic.getDepartmentId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis topic not found");
        }
        boolean published = isRoundResultsPublished(topic.getRoundId());
        boolean studentScoped = isStudentScoped(actor);
        String callerStudentId = normalize(actor == null ? null : actor.getClaimAsString("studentId"));
        boolean participant = published && studentScoped && !callerStudentId.isEmpty()
                && !scope.findParticipatedTopicIds(callerStudentId, List.of(topic.getId())).isEmpty();
        return maskScoreIfNeeded(TopicResponse.from(topic, supervisorsOf(topic.getId())), published,
                deanInScope || isCouncilGraderOf(topic.getId(), actor),
                normalize(actor == null ? null : actor.getClaimAsString("lecturerId")),
                !studentScoped || participant);
    }

    /**
     * Scores stay internal until the round publishes results: students (and
     * lecturers who are not grading the topic) must not read a finalized
     * council score off the topic resource early. Governance staff and the
     * council members assigned to the topic keep full visibility. Publication
     * opens the outcome only to staff and to the student whose approved group
     * took the topic — a participant-scoped caller still sees other topics'
     * results masked, matching what {@code /me/results} returns.
     */
    private TopicResponse maskScoreIfNeeded(TopicResponse topic, boolean roundPublished, boolean councilGrader,
            String callerLecturerId, boolean outcomeAuthorized) {
        // The assigned GVPB reviewer is a grading stakeholder too: they need
        // the finalization state to know the counter-review window is closed,
        // while a plain student or unrelated lecturer never does.
        boolean assignedReviewer = topic.gvpbLecturerId() != null
                && topic.gvpbLecturerId().equals(callerLecturerId);
        boolean internalGrader = councilGrader || assignedReviewer;
        boolean scoreMasked = (!roundPublished || !outcomeAuthorized) && !internalGrader
                && (topic.finalScore() != null || topic.resultStatus() != null);
        // The GVPB reviewer identity is internal governance data: students and
        // unrelated lecturers must not learn who counter-reviews a topic.
        boolean reviewerMasked = topic.gvpbLecturerId() != null
                && !internalGrader;
        // The waiver and outcome annotations are governance context: they ship
        // with the same visibility as the outcome fields they describe.
        boolean governanceMasked = (topic.gvpbWaivedReason() != null || topic.resultReason() != null)
                && !internalGrader;
        if (!scoreMasked && !reviewerMasked && !governanceMasked) {
            return topic;
        }
        return new TopicResponse(
                topic.id(), topic.roundId(), topic.departmentId(), topic.title(), topic.description(),
                topic.maxGroups(), topic.status(), topic.createdBy(),
                scoreMasked ? null : topic.finalScore(),
                scoreMasked ? null : topic.resultStatus(),
                reviewerMasked ? null : topic.gvpbLecturerId(),
                governanceMasked ? null : topic.gvpbWaivedReason(),
                governanceMasked ? null : topic.resultReason(),
                topic.supervisors());
    }

    private List<TopicSupervisorEntry> supervisorsOf(UUID topicId) {
        return scope.findSupervisorsByTopicIds(List.of(topicId)).getOrDefault(topicId, List.of());
    }

    /**
     * A caller with no staff or grading role is scoped to participation: the
     * only published outcome they may read on a topic is the one their own
     * approved group earned — staff roles and lecturers keep the published
     * view they had before.
     */
    private static boolean isStudentScoped(Jwt actor) {
        return !isAdmin(actor) && !ThesisDeanScope.isFacultyHead(actor)
                && !ThesisDepartmentScope.isDepartmentHead(actor) && !isLecturer(actor);
    }

    private boolean isCouncilGraderOf(UUID topicId, Jwt actor) {
        if (isAdmin(actor)) {
            return true;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (lecturerId.isEmpty()) {
            return false;
        }
        Integer member = scope.countCouncilGrader(topicId, lecturerId);
        return member != null && member > 0;
    }

    private boolean isRoundResultsPublished(UUID roundId) {
        Integer published = scope.countPublishedResultsRounds(roundId);
        return published != null && published > 0;
    }

    private static boolean isOwner(ThesisTopic topic, Jwt actor) {
        // authorizeTopicOwner in the mutation service accepts either the user
        // subject or the lecturerId claim — this read-side check must agree.
        String actorId = subject(actor);
        String lecturerId = actor == null ? "" : normalize(actor.getClaimAsString("lecturerId"));
        return (!actorId.isEmpty() && topic.getCreatedBy().equals(actorId))
                || (!lecturerId.isEmpty() && topic.getCreatedBy().equals(lecturerId));
    }

    private static String subject(Jwt actor) {
        String subject = actor == null ? null : actor.getSubject();
        return subject == null ? "" : subject.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isAdmin(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
    }

    private static boolean isLecturer(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("LECTURER");
    }
}
