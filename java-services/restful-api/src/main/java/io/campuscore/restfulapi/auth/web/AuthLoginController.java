package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.AuthLoginService.LoginResult;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Disabled-by-default Java login endpoint for the monolith migration. */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.auth-login", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/auth")
public class AuthLoginController {

    private final AuthLoginService auth;
    private final SessionCookieService cookies;

    public AuthLoginController(AuthLoginService auth, SessionCookieService cookies) {
        this.auth = auth;
        this.cookies = cookies;
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
}
