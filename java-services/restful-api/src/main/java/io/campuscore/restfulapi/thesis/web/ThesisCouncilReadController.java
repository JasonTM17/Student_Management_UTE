package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilReadService;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilReadDtos.CouncilResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/thesis/councils")
public class ThesisCouncilReadController {

    private final ThesisCouncilReadService councils;

    public ThesisCouncilReadController(ThesisCouncilReadService councils) {
        this.councils = councils;
    }

    @GetMapping
    public List<CouncilResponse> list(@RequestParam UUID roundId) {
        return councils.list(roundId);
    }
}
