package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisGroupReadService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/thesis/groups")
public class ThesisGroupReadController {
    private final ThesisGroupReadService groups;
    public ThesisGroupReadController(ThesisGroupReadService groups) { this.groups = groups; }
    @GetMapping public List<GroupResponse> list(@RequestParam UUID roundId) { return groups.list(roundId); }
    @GetMapping("/{id}") public GroupResponse get(@PathVariable UUID id) { return groups.get(id); }
}
