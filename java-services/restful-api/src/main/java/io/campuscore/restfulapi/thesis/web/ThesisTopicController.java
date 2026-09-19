package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Thesis Topics Catalog", description = "Quản lý và tra cứu kho đề tài Khóa luận & Đồ án tốt nghiệp theo đợt và bộ môn")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/topics")
public class ThesisTopicController {

    private final ThesisTopicService topics;

    public ThesisTopicController(ThesisTopicService topics) {
        this.topics = topics;
    }

    @Operation(summary = "Lấy danh sách đề tài theo đợt khóa luận", description = "Truy xuất danh sách các đề tài khóa luận trong đợt đã chọn, có quyền bảo mật ẩn đề tài nháp/lưu trữ đối với sinh viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách đề tài thành công"),
        @ApiResponse(responseCode = "400", description = "Tham số roundId không hợp lệ")
    })
    @GetMapping
    public List<TopicResponse> list(
            @Parameter(description = "Mã định danh UUID của đợt khóa luận", required = true)
            @RequestParam UUID roundId,
            @Parameter(description = "Trạng thái đề tài (PUBLISHED, APPROVED, DRAFT...)")
            @RequestParam(required = false) TopicStatus status,
            @AuthenticationPrincipal Jwt actor) {
        return topics.list(roundId, status, actor);
    }

    @Operation(summary = "Xem chi tiết đề tài theo mã định danh (UUID)", description = "Truy xuất thông tin chi tiết một đề tài khóa luận bao gồm mô tả, mục tiêu, GVHD và số nhóm tối đa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy đề tài"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đề tài với UUID tương ứng")
    })
    @GetMapping("/{id}")
    public TopicResponse get(
            @Parameter(description = "Mã UUID của đề tài", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return topics.get(id, actor);
    }
}
