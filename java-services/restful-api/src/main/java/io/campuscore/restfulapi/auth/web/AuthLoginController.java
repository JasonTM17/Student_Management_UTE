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
import io.campuscore.restfulapi.auth.web.AuthDtos.RegisterRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.UpdateProfileRequest;
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
public class AuthLoginController {

    private final AuthLoginService auth;
    private final SessionCookieService cookies;

    public AuthLoginController(AuthLoginService auth, SessionCookieService cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("register")
    public LoginResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LoginResult result = auth.register(
                request,
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

    @PostMapping("login")
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
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }

    @PutMapping("profile")
    public AuthUserResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) UpdateProfileRequest request) {
        return auth.updateProfile(jwt.getSubject(), request);
    }

    @PostMapping("change-password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(jwt.getSubject(), request.oldPassword(), request.newPassword());
        return new MessageResponse("Password changed successfully");
    }

    @PostMapping("logout")
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
