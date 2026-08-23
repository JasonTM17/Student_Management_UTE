package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.service.ThesisRoundReadService;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/rounds")
public class ThesisRoundReadController {

    private final ThesisRoundReadService rounds;

    public ThesisRoundReadController(ThesisRoundReadService rounds) {
        this.rounds = rounds;
    }

    @GetMapping
    public List<RoundResponse> list(@RequestParam(required = false) RoundStatus status) {
        return rounds.list(status);
    }
}
