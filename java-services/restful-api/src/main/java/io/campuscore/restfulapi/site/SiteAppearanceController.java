package io.campuscore.restfulapi.site;

import com.fasterxml.jackson.databind.JsonNode;
import io.campuscore.restfulapi.web.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site-wide appearance (accent theme, homepage hero copy, homepage post order).
 *
 * <p>GET is anonymous by design: the homepage renders this chrome for visitors
 * who have not signed in. PUT is administrator-only, and the filter chain keeps
 * the anonymous rule on the GET verb alone. The stored payload is the same JSON
 * object the frontend editor produces; the frontend re-sanitizes on every read,
 * and this controller only enforces that it is a JSON object within the size
 * bound. The write stamp ({@code version}, {@code updatedAt}) is applied by
 * {@link SiteAppearanceStore}, so both the PUT response and every later GET
 * carry the same change marker a polling browser compares against.
 */
@Tag(name = "Site Appearance", description = "Quản lý cấu hình giao diện, màu sắc chủ đề và nội dung trang chủ toàn hệ thống")
@RestController
@RequestMapping(path = "/api/v1/site-appearance", produces = MediaType.APPLICATION_JSON_VALUE)
public class SiteAppearanceController {

    private final SiteAppearanceStore store;

    public SiteAppearanceController(SiteAppearanceStore store) {
        this.store = store;
    }

    @Operation(summary = "Lấy cấu hình giao diện hệ thống", description = "Truy xuất cấu hình giao diện công khai: bảng màu, khẩu hiệu trang chủ, thứ tự bài viết")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất cấu hình giao diện thành công")
    })
    @GetMapping
    public String read() {
        String payload = store.read();
        return payload == null ? "{}" : payload;
    }

    @Operation(summary = "Cập nhật cấu hình giao diện hệ thống", description = "Lưu cấu hình giao diện mới dành cho người quản trị (ADMIN, SUPER_ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật cấu hình giao diện thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu cấu hình không hợp lệ hoặc vượt quá dung lượng cho phép"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String save(@RequestBody JsonNode body, Authentication authentication) {
        if (body == null || !body.isObject()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "appearance_payload_invalid",
                    "Appearance payload must be a JSON object");
        }
        if (body.toString().length() > SiteAppearanceStore.MAX_PAYLOAD_CHARS) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "appearance_payload_too_large",
                    "Appearance payload exceeds the size limit");
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt) || jwt.getSubject() == null) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "appearance_not_authenticated",
                    "Not authenticated");
        }
        return store.write(body.toString(), jwt.getSubject());
    }
}
