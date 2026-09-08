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

    public ThesisTopicService(ThesisTopicRepository topics, ThesisRoundReadPort rounds) {
        this.topics = topics;
        this.rounds = rounds;
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
        if (requestedStatus == TopicStatus.PUBLISHED || isAdmin(actor)) {
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
        return result.stream()
                .map(TopicResponse::from)
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
        if (topic.getStatus() != TopicStatus.PUBLISHED && !isAdmin(actor) && !isOwner(topic, actor)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis topic not found");
        }
        return TopicResponse.from(topic);
    }

    private static boolean isOwner(ThesisTopic topic, Jwt actor) {
        String actorId = subject(actor);
        return !actorId.isEmpty() && topic.getCreatedBy().equals(actorId);
    }

    private static String subject(Jwt actor) {
        String subject = actor == null ? null : actor.getSubject();
        return subject == null ? "" : subject.trim();
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
