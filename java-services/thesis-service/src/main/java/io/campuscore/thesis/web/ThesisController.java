package io.campuscore.thesis.web;

import io.campuscore.thesis.domain.RoundStatus;
import io.campuscore.thesis.domain.TopicStatus;
import io.campuscore.thesis.security.AccessContext;
import io.campuscore.thesis.service.ThesisGroupService;
import io.campuscore.thesis.service.ThesisRoundService;
import io.campuscore.thesis.service.ThesisTopicService;
import io.campuscore.thesis.web.ThesisDtos.AddMemberRequest;
import io.campuscore.thesis.web.ThesisDtos.AssignTopicRequest;
import io.campuscore.thesis.web.ThesisDtos.CreateGroupRequest;
import io.campuscore.thesis.web.ThesisDtos.CreateRoundRequest;
import io.campuscore.thesis.web.ThesisDtos.CreateTopicRequest;
import io.campuscore.thesis.web.ThesisDtos.DecideGroupRequest;
import io.campuscore.thesis.web.ThesisDtos.GroupResponse;
import io.campuscore.thesis.web.ThesisDtos.RoundResponse;
import io.campuscore.thesis.web.ThesisDtos.TopicResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/thesis")
public class ThesisController {

    private final ThesisRoundService rounds;
    private final ThesisTopicService topics;
    private final ThesisGroupService groups;

    public ThesisController(ThesisRoundService rounds, ThesisTopicService topics, ThesisGroupService groups) {
        this.rounds = rounds;
        this.topics = topics;
        this.groups = groups;
    }

    @GetMapping("/rounds")
    public List<RoundResponse> listRounds(@RequestParam(required = false) RoundStatus status) {
        return rounds.list(status);
    }

    @PostMapping("/rounds")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:round:create')")
    public RoundResponse createRound(@Valid @RequestBody CreateRoundRequest request) {
        return rounds.create(request);
    }

    @PostMapping("/rounds/{id}/open-registration")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:round:manage')")
    public RoundResponse openRegistration(@PathVariable UUID id) {
        return rounds.openRegistration(id);
    }

    @PostMapping("/rounds/{id}/close-registration")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:round:manage')")
    public RoundResponse closeRegistration(@PathVariable UUID id) {
        return rounds.closeRegistration(id);
    }

    @PostMapping("/rounds/{id}/publish-proposals")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:round:publish')")
    public RoundResponse publishProposals(@PathVariable UUID id) {
        return rounds.publishProposals(id);
    }

    @GetMapping("/topics")
    public List<TopicResponse> listTopics(
            @RequestParam UUID roundId,
            @RequestParam(required = false) TopicStatus status) {
        return topics.list(roundId, status);
    }

    @PostMapping("/topics")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:topic:create')")
    public TopicResponse createTopic(
            @Valid @RequestBody CreateTopicRequest request,
            Authentication authentication) {
        return topics.create(request, AccessContext.from(authentication).userId());
    }

    @PostMapping("/topics/{id}/publish")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:topic:publish')")
    public TopicResponse publishTopic(@PathVariable UUID id) {
        return topics.publish(id);
    }

    @GetMapping("/groups")
    public List<GroupResponse> listGroups(@RequestParam UUID roundId) {
        return groups.list(roundId);
    }

    @GetMapping("/groups/{id}")
    public GroupResponse getGroup(@PathVariable UUID id) {
        return groups.getResponse(id);
    }

    @PostMapping("/groups")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:group:create')")
    public GroupResponse createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication) {
        return groups.create(request, AccessContext.from(authentication).studentId());
    }

    @PostMapping("/groups/{id}/members")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:group:manage')")
    public GroupResponse addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication) {
        return groups.addMember(id, request, AccessContext.from(authentication).studentId());
    }

    @PostMapping("/groups/{id}/topic")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:group:manage')")
    public GroupResponse assignTopic(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTopicRequest request,
            Authentication authentication) {
        return groups.assignTopic(id, request, AccessContext.from(authentication).studentId());
    }

    @PostMapping("/groups/{id}/decision")
    @PreAuthorize("@thesisPermissions.has(authentication, 'thesis:group:approve')")
    public GroupResponse decideGroup(
            @PathVariable UUID id,
            @Valid @RequestBody DecideGroupRequest request,
            Authentication authentication) {
        return groups.decide(id, request, AccessContext.from(authentication).userId());
    }
}
