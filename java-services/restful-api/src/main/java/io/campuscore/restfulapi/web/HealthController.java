package io.campuscore.restfulapi.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Probes", description = "Kiểm tra trạng thái sẵn sàng (Readiness) và khả năng hoạt động (Liveness) của dịch vụ")
public class HealthController {

    private final String readinessKey;
    private final JdbcOperations jdbc;

    public HealthController(
            @Value("${health.readiness-key:}") String readinessKey,
            JdbcOperations jdbc) {
        this.readinessKey = readinessKey;
        this.jdbc = jdbc;
    }

    @GetMapping("/liveness")
    @Operation(summary = "Kiểm tra Liveness Probe", description = "Xác nhận container RESTful API đang chạy bình thường.")
    @ApiResponse(responseCode = "200", description = "Dịch vụ đang hoạt động bình thường")
    public Map<String, Object> liveness() {
        return Map.of(
                "status", "ok",
                "service", "restful-api",
                "timestamp", Instant.now());
    }

    @GetMapping("/readiness")
    @Operation(summary = "Kiểm tra Readiness Probe", description = "Kiểm tra kết nối tới cơ sở dữ liệu PostgreSQL trước khi tiếp nhận lưu lượng truy cập.")
    @ApiResponse(responseCode = "200", description = "Cơ sở dữ liệu sẵn sàng")
    public Map<String, Object> readiness(
            @Parameter(description = "Mã khóa bảo vệ probe kiểm tra trạng thái sẵn sàng")
            @RequestHeader(value = "X-Health-Key", required = false) String suppliedKey) {
        if (readinessKey.isBlank()
                || suppliedKey == null
                || !MessageDigest.isEqual(
                        readinessKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Readiness key required");
        }

        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Database unavailable",
                    exception);
        }

        return Map.of(
                "status", "ready",
                "service", "restful-api",
                "dependencies", List.of("postgresql"),
                "timestamp", Instant.now());
    }
}
