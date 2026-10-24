package io.campuscore.restfulapi.auth.controller;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.model.LoginResult;
import io.campuscore.restfulapi.auth.service.PasswordResetRequestDispatcher;
import io.campuscore.restfulapi.auth.service.PasswordResetService;
import io.campuscore.restfulapi.security.ratelimit.service.ClientIpResolver;
import io.campuscore.restfulapi.auth.dto.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.ChangePasswordRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.ChangePasswordResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.ForgotPasswordRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.LoginRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.LogoutRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.MessageResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.RefreshRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.ResetPasswordRequest;
import io.campuscore.restfulapi.auth.dto.AuthDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.campuscore.restfulapi.auth.dto.AuthDtos;
import io.campuscore.restfulapi.auth.service.SessionCookieService;

/** Java auth session endpoints owned by the course REST API. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Session", description = "Xác thực tài khoản người dùng, cấp phát JWT Token, làm mới phiên và đổi mật khẩu")
public class AuthLoginController {

    private final AuthLoginService auth;
    private final SessionCookieService cookies;
    private final PasswordResetService passwordResets;
    private final PasswordResetRequestDispatcher passwordResetRequests;
    private final ClientIpResolver clientIps;

    public AuthLoginController(
            AuthLoginService auth,
            SessionCookieService cookies,
            PasswordResetService passwordResets,
            PasswordResetRequestDispatcher passwordResetRequests,
            ClientIpResolver clientIps) {
        this.auth = auth;
        this.cookies = cookies;
        this.passwordResets = passwordResets;
        this.passwordResetRequests = passwordResetRequests;
        this.clientIps = clientIps;
    }

    @PostMapping("login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực tài khoản bằng email và mật khẩu, trả về cặp Access Token và Refresh Token cùng thông tin vai trò. Với tài khoản đã bật xác thực hai yếu tố, trả về twoFactorRequired=true kèm challengeId và email che khuất thay cho token.")
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
                clientIps.resolve(servletRequest),
                servletRequest.getHeader("User-Agent"));
        // Two-factor accounts get a challenge, not a session: no tokens and
        // deliberately no auth cookies. The client finishes via
        // POST /api/v1/auth/two-factor/verify.
        if (result.response() == null) {
            return new LoginResponse(
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    result.challengeId(),
                    result.email());
        }
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
                clientIps.resolve(servletRequest),
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
    public ChangePasswordResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Optional<LoginResult> result = auth.changePassword(
                jwt.getSubject(),
                request.oldPassword(),
                request.newPassword(),
                cookies.refreshToken(servletRequest, request.refreshToken()));
        if (result.isEmpty()) {
            // No session could be pinned, so every session was revoked —
            // clear the dead credential cookies and say so plainly.
            cookies.clear(servletResponse);
            return new ChangePasswordResponse(
                    "Password changed successfully", false, null, null, null);
        }
        cookies.issue(
                servletRequest,
                servletResponse,
                result.get().response().accessToken(),
                result.get().response().refreshToken(),
                result.get().accessTokenExpiresAt(),
                result.get().refreshTokenExpiresAt());
        return new ChangePasswordResponse(
                "Password changed successfully",
                true,
                result.get().response().user(),
                result.get().response().accessToken(),
                result.get().response().refreshToken());
    }

    @PostMapping("forgot-password")
    @Operation(summary = "Yêu cầu đặt lại mật khẩu", description = "Gửi liên kết đặt lại mật khẩu tới email của tài khoản. Phản hồi luôn giống nhau dù email có tồn tại hay không, để tránh lộ thông tin tài khoản.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tiếp nhận yêu cầu; nếu tài khoản khớp, liên kết đặt lại sẽ được gửi"),
            @ApiResponse(responseCode = "429", description = "Vượt quá số lần yêu cầu cho phép")
    })
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        // Enqueued, not awaited: the reply must cost the same whether the
        // mailbox exists or not — the work runs on the shared async executor.
        passwordResetRequests.requestResetAsync(request.email(), clientIps.resolve(servletRequest));
        return new MessageResponse(PasswordResetService.GENERIC_RESPONSE_MESSAGE);
    }

    @PostMapping("reset-password")
    @Operation(summary = "Đặt lại mật khẩu bằng liên kết", description = "Đặt mật khẩu mới từ liên kết một lần được gửi qua email; liên kết chỉ dùng được một lần trong 30 phút.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đặt lại mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Liên kết không hợp lệ, đã hết hạn hoặc mật khẩu mới không đạt chính sách")
    })
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResets.resetPassword(request.token(), request.newPassword());
        return new MessageResponse(PasswordResetService.SUCCESS_RESPONSE_MESSAGE);
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
