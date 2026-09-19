package io.campuscore.restfulapi.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small authenticated contract probe for web/mobile integration checks. */
@RestController
@RequestMapping("/api/v1/contract")
@Tag(name = "System Contract & Probing", description = "Kiểm tra phiên bản giao ước API và tính tương thích tích hợp Web/Mobile")
public class ContractController {

    @GetMapping
    @Operation(summary = "Kiểm tra phiên bản giao ước API", description = "Trả về thông số kiến trúc, phiên bản API và trạng thái sẵn sàng của dịch vụ RESTful.")
    @ApiResponse(responseCode = "200", description = "Thông số phiên bản hệ thống")
    public Map<String, Object> contract() {
        return Map.of(
                "apiVersion", "v1",
                "application", "restful-api",
                "status", "course-ready",
                "architecture", "single-java-api");
    }

    @PostMapping("/ping")
    @Operation(summary = "Ping kiểm tra phản hồi API", description = "Gửi thông điệp kiểm tra và nhận lại phản hồi dội lại (echo) từ dịch vụ.")
    @ApiResponse(responseCode = "200", description = "Dội lại thông điệp thành công")
    public ResponseEntity<Map<String, Object>> ping(@Valid @RequestBody PingRequest request) {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "echo", request.message(),
                "writer", "restful-api"));
    }

    public record PingRequest(@NotBlank(message = "message is required") String message) {
    }
}
