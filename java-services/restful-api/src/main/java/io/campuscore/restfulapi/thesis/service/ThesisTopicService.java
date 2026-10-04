package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Profile("persistence")
public class ThesisTopicService {

    private final ThesisTopicRepository topics;
    private final ThesisRoundReadPort rounds;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    public ThesisTopicService(ThesisTopicRepository topics, ThesisRoundReadPort rounds,
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        this.topics = topics;
        this.rounds = rounds;
        this.jdbc = jdbc;
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
        if (requestedStatus == TopicStatus.PUBLISHED || requestedStatus == TopicStatus.APPROVED || isAdmin(actor)) {
            if (requestedStatus == TopicStatus.PUBLISHED) {
                List<ThesisTopic> published = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, TopicStatus.PUBLISHED);
                List<ThesisTopic> approved = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, TopicStatus.APPROVED);
                result = new java.util.ArrayList<>(published);
                result.addAll(approved);
                result.sort(java.util.Comparator.comparing(ThesisTopic::getTitle));
            } else {
                result = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus);
            }
        } else if (!isLecturer(actor)) {
            result = List.of();
        } else {
            String actorId = subject(actor);
            result = topics.findAllByRoundIdAndStatusOrderByTitle(roundId, requestedStatus)
                    .stream()
                    .filter(topic -> topic.getCreatedBy().equals(actorId))
                    .toList();
        }
        boolean published = isRoundResultsPublished(roundId);
        // One query for every topic the caller grades as a council member, so
        // the mask check below does not turn into per-topic round-trips.
        java.util.Set<UUID> gradingTopicIds = new java.util.HashSet<>();
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!published && !isAdmin(actor) && !lecturerId.isEmpty() && !result.isEmpty()) {
            gradingTopicIds.addAll(jdbc.queryForList(
                    "SELECT ct.topic_id FROM thesis.thesis_council_topic ct "
                            + "JOIN thesis.thesis_council_member m ON m.council_id = ct.council_id "
                            + "WHERE m.lecturer_id = :lecturerId AND ct.topic_id IN (:topicIds)",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("lecturerId", lecturerId)
                            .addValue("topicIds", result.stream().map(ThesisTopic::getId).toList()),
                    UUID.class));
        }
        boolean adminCaller = isAdmin(actor);
        String callerLecturerId = lecturerId;
        return result.stream()
                .map(TopicResponse::from)
                .map(topic -> maskScoreIfNeeded(topic, published,
                        adminCaller || gradingTopicIds.contains(topic.id()),
                        callerLecturerId))
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
        if (topic.getStatus() != TopicStatus.PUBLISHED && topic.getStatus() != TopicStatus.APPROVED && !isAdmin(actor) && !isOwner(topic, actor)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis topic not found");
        }
        return maskScoreIfNeeded(TopicResponse.from(topic), isRoundResultsPublished(topic.getRoundId()),
                isCouncilGraderOf(topic.getId(), actor),
                normalize(actor == null ? null : actor.getClaimAsString("lecturerId")));
    }

    /**
     * Scores stay internal until the round publishes results: students (and
     * lecturers who are not grading the topic) must not read a finalized
     * council score off the topic resource early. Governance staff and the
     * council members assigned to the topic keep full visibility; the
     * supervisor sees the result with everyone else at publication.
     */
    private TopicResponse maskScoreIfNeeded(TopicResponse topic, boolean roundPublished, boolean councilGrader,
            String callerLecturerId) {
        // The assigned GVPB reviewer is a grading stakeholder too: they need
        // the finalization state to know the counter-review window is closed,
        // while a plain student or unrelated lecturer never does.
        boolean assignedReviewer = topic.gvpbLecturerId() != null
                && topic.gvpbLecturerId().equals(callerLecturerId);
        boolean internalGrader = councilGrader || assignedReviewer;
        boolean scoreMasked = !roundPublished && !internalGrader
                && (topic.finalScore() != null || topic.resultStatus() != null);
        // The GVPB reviewer identity is internal governance data: students and
        // unrelated lecturers must not learn who counter-reviews a topic.
        boolean reviewerMasked = topic.gvpbLecturerId() != null
                && !internalGrader;
        if (!scoreMasked && !reviewerMasked) {
            return topic;
        }
        return new TopicResponse(
                topic.id(), topic.roundId(), topic.departmentId(), topic.title(), topic.description(),
                topic.maxGroups(), topic.status(), topic.createdBy(),
                scoreMasked ? null : topic.finalScore(),
                scoreMasked ? null : topic.resultStatus(),
                reviewerMasked ? null : topic.gvpbLecturerId());
    }

    private boolean isCouncilGraderOf(UUID topicId, Jwt actor) {
        if (isAdmin(actor)) {
            return true;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (lecturerId.isEmpty()) {
            return false;
        }
        Integer member = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_council_member m ON m.council_id = ct.council_id "
                        + "WHERE ct.topic_id = :topicId AND m.lecturer_id = :lecturerId",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("topicId", topicId)
                        .addValue("lecturerId", lecturerId),
                Integer.class);
        return member != null && member > 0;
    }

    private boolean isRoundResultsPublished(UUID roundId) {
        Integer published = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE id = :roundId AND status = 'RESULTS_PUBLISHED'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("roundId", roundId),
                Integer.class);
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
        return roles != null && (roles.contains("ADMIN") || roles.contains("TRUONG_KHOA") || roles.contains("SUPER_ADMIN"));
    }

    private static boolean isLecturer(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("LECTURER");
    }
}
