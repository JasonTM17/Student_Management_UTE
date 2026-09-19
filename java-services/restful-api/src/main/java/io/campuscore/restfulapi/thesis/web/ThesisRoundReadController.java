package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.service.ThesisRoundReadService;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Thesis Registration Rounds", description = "Quản lý và tra cứu các đợt đăng ký Khóa luận tốt nghiệp, Tiểu luận chuyên ngành và Đồ án tốt nghiệp")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/rounds")
public class ThesisRoundReadController {

    private final ThesisRoundReadService rounds;

    public ThesisRoundReadController(ThesisRoundReadService rounds) {
        this.rounds = rounds;
    }

    @Operation(summary = "Lấy danh sách các đợt đăng ký khóa luận", description = "Truy xuất danh sách các đợt khóa luận tốt nghiệp theo trạng thái học thuật (REGISTRATION_OPEN, PROPOSAL_OPEN, RESULTS_PUBLISHED, ...)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách đợt thành công")
    })
    @GetMapping
    public List<RoundResponse> list(
            @Parameter(description = "Lọc theo trạng thái đợt (PROPOSAL_OPEN, REGISTRATION_OPEN, RESULTS_PUBLISHED, v.v.)")
            @RequestParam(required = false) RoundStatus status) {
        return rounds.list(status);
    }
}
