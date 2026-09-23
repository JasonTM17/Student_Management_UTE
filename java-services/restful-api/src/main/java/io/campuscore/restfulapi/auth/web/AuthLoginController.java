package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.AuthLoginService.LoginResult;
import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.ChangePasswordRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.LogoutRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.MessageResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.RefreshRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Java auth session endpoints owned by the course REST API. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Session", description = "Xác thực tài khoản người dùng, cấp phát JWT Token, làm mới phiên và đổi mật khẩu")
public class AuthLoginController {

    private final AuthLoginService auth;
    private final SessionCookieService cookies;

    public AuthLoginController(AuthLoginService auth, SessionCookieService cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực tài khoản bằng email và mật khẩu, trả về cặp Access Token và Refresh Token cùng thông tin vai trò.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, phát hành token"),
            @ApiResponse(responseCode = "401", description = "Email hoặc mật khẩu không chính xác"),
            @ApiResponse(responseCode = "429", description = "Vượt quá số lần thử đăng nhập cho phép")
    })
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LoginResult result = auth.login(
                request.email(),
                request.password(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        cookies.issue(
                servletRequest,
                servletResponse,
                result.response().accessToken(),
                result.response().refreshToken(),
                result.accessTokenExpiresAt(),
                result.refreshTokenExpiresAt());
        return result.response();
    }

    @PostMapping("refresh")
    @Operation(summary = "Làm mới Access Token", description = "Cấp mới Access Token bằng Refresh Token hợp lệ khi phiên làm việc sắp hết hạn.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Làm mới phiên thành công, trả về token mới"),
            @ApiResponse(responseCode = "401", description = "Refresh Token không hợp lệ hoặc đã hết hạn")
    })
    public LoginResponse refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LoginResult result = auth.refresh(
                cookies.refreshToken(servletRequest, request == null ? null : request.refreshToken()),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        cookies.issue(
                servletRequest,
                servletResponse,
                result.response().accessToken(),
                result.response().refreshToken(),
                result.accessTokenExpiresAt(),
                result.refreshTokenExpiresAt());
        return result.response();
    }

    @GetMapping("me")
    @Operation(summary = "Lấy hồ sơ tài khoản hiện tại", description = "Truy xuất thông tin người dùng, quyền hạn và vai trò liên kết với phiên đăng nhập hiện hành.")
    @ApiResponse(responseCode = "200", description = "Trả về hồ sơ tài khoản đang đăng nhập")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }

    @PutMapping("profile")
    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Cập nhật thông tin họ tên hiển thị của tài khoản hiện tại.")
    @ApiResponse(responseCode = "200", description = "Hồ sơ đã được cập nhật thành công")
    public AuthUserResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) UpdateProfileRequest request) {
        return auth.updateProfile(jwt.getSubject(), request);
    }

    @PostMapping("change-password")
    @Operation(summary = "Đổi mật khẩu người dùng", description = "Thay đổi mật khẩu tài khoản bằng cách xác minh mật khẩu cũ và cung cấp mật khẩu mới.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu cũ không đúng hoặc mật khẩu mới không hợp lệ")
    })
    public MessageResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(jwt.getSubject(), request.oldPassword(), request.newPassword());
        return new MessageResponse("Password changed successfully");
    }

    @PostMapping("logout")
    @Operation(summary = "Đăng xuất tài khoản", description = "Thu hồi Refresh Token và xóa toàn bộ cookie xác thực an toàn.")
    @ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
    public Map<String, String> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        auth.logout(
                jwt.getSubject(),
                cookies.refreshToken(servletRequest, request == null ? null : request.refreshToken()));
        cookies.clear(servletResponse);
        return Map.of("message", "Logged out successfully");
    }
}
