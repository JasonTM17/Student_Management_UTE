package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisMutationController {

    private final ThesisMutationService mutations;

    public ThesisMutationController(ThesisMutationService mutations) {
        this.mutations = mutations;
    }

    @PostMapping("/rounds")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse createRound(@RequestBody RoundCreateRequest request) {
        return mutations.createRound(request);
    }

    @PostMapping("/rounds/{id}/open-registration")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse openRegistration(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.DRAFT, RoundStatus.REGISTRATION_OPEN);
    }

    @PostMapping("/rounds/{id}/close-registration")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse closeRegistration(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.REGISTRATION_OPEN, RoundStatus.REGISTRATION_CLOSED);
    }

    @PostMapping("/rounds/{id}/publish-proposals")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse publishProposals(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.REGISTRATION_CLOSED, RoundStatus.PROPOSALS_PUBLISHED);
    }

    @PostMapping("/topics")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    public TopicResponse createTopic(
            @RequestBody TopicCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createTopic(request, actor);
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    public TopicResponse updateTopic(
            @PathVariable UUID id,
            @RequestBody TopicUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateTopic(id, request, actor);
    }

    @PostMapping("/topics/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    public TopicResponse publishTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return mutations.publishTopic(id, actor);
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','SUPER_ADMIN')")
    public GroupResponse createGroup(
            @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createGroup(request, actor);
    }

    @PostMapping("/groups/{id}/members")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','SUPER_ADMIN')")
    public GroupResponse addMember(
            @PathVariable UUID id,
            @RequestBody MemberRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.addMember(id, request, actor);
    }

    @DeleteMapping("/groups/{id}/members/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','SUPER_ADMIN')")
    public GroupResponse removeMember(
            @PathVariable UUID id,
            @PathVariable String studentId,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.removeMember(id, studentId, actor);
    }

    @PostMapping("/groups/{id}/topic")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','SUPER_ADMIN')")
    public GroupResponse assignTopic(
            @PathVariable UUID id,
            @RequestBody TopicAssignmentRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.assignTopic(id, request, actor);
    }

    @PatchMapping("/groups/{id}/progress")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','SUPER_ADMIN')")
    public GroupResponse updateProgress(
            @PathVariable UUID id,
            @RequestBody ProgressRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateProgress(id, request, actor);
    }
}
