package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicScheduleReadService;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleListResponse;
import io.campuscore.restfulapi.academic.web.AcademicScheduleReadDtos.ScheduleResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Academic schedule query routes. */
@Tag(name = "Academic Schedules", description = "Tra cứu thời khóa biểu học tập, phòng học và lịch giảng dạy")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/schedules")
public class AcademicScheduleReadController {

    private final AcademicScheduleReadService academic;

    public AcademicScheduleReadController(AcademicScheduleReadService academic) {
        this.academic = academic;
    }

    @Operation(summary = "Danh sách lịch học / thời khóa biểu", description = "Truy vấn danh sách các tiết học, phòng học, thứ trong tuần và thời gian học")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy danh sách thời khóa biểu thành công")
    })
    @GetMapping
    public ScheduleListResponse getSchedules(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findSchedules(page, limit);
    }

    @Operation(summary = "Chi tiết một mục thời khóa biểu", description = "Lấy thông tin chi tiết một buổi học theo mã định danh")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy lịch học")
    })
    @GetMapping("{id}")
    public ScheduleResponse getSchedule(
            @Parameter(description = "Mã định danh lịch học (UUID)", required = true) @PathVariable String id) {
        return academic.findSchedule(id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
