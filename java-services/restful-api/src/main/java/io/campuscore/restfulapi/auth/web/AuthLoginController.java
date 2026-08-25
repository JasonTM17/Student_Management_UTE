package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.AuthLifecycleService;
import io.campuscore.restfulapi.auth.service.AuthLoginService.LoginResult;
import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.ChallengeTokenRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.ChangePasswordRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.EmailRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.LogoutRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.MessageResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.PasswordResetConfirmRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.PasswordResetRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.RegistrationPendingResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.RefreshRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.RegisterRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Java auth session endpoints owned by the course REST API. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/auth")
public class AuthLoginController {

    private final AuthLoginService auth;
    private final AuthLifecycleService lifecycle;
    private final SessionCookieService cookies;

    public AuthLoginController(AuthLoginService auth, AuthLifecycleService lifecycle, SessionCookieService cookies) {
        this.auth = auth;
        this.lifecycle = lifecycle;
        this.cookies = cookies;
    }

    @PostMapping("register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistrationPendingResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        return auth.register(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
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

    @PostMapping("email-verifications/confirm")
    public MessageResponse confirmEmail(@Valid @RequestBody ChallengeTokenRequest request) {
        return lifecycle.confirmEmail(request);
    }

    @PostMapping("email-verifications/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        return lifecycle.resendVerification(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("password-reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        return lifecycle.requestPasswordReset(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("password-reset/confirm")
    public MessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return lifecycle.confirmPasswordReset(request);
    }

    @PostMapping("resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerificationAlias(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        return resendVerification(request, servletRequest);
    }

    @PostMapping("forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPasswordAlias(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        return requestPasswordReset(request, servletRequest);
    }

    @PostMapping("reset-password")
    public MessageResponse resetPasswordAlias(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return confirmPasswordReset(request);
    }

    @PostMapping("verify-email")
    public MessageResponse verifyEmailAlias(@Valid @RequestBody ChallengeTokenRequest request) {
        return confirmEmail(request);
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
