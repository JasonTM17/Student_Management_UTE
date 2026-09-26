package io.campuscore.restfulapi.auth.web;

import io.campuscore.restfulapi.auth.service.AuthLoginService;
import io.campuscore.restfulapi.auth.service.AuthLoginService.LoginResult;
import io.campuscore.restfulapi.auth.service.TwoFactorService;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.ChallengeResponse;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.ConfirmTwoFactorRequest;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.DisableTwoFactorRequest;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.EnableTwoFactorRequest;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.TwoFactorStatusResponse;
import io.campuscore.restfulapi.auth.web.TwoFactorDtos.VerifyTwoFactorLoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opt-in email OTP two-factor authentication: manage the per-account switch
 * and finish the second login step. Accounts with the switch off keep the
 * unchanged single-step login contract.
 */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
@Tag(name = "Two-Factor Authentication", description = "Bật/tắt xác thực hai yếu tố qua email và hoàn tất đăng nhập bằng mã OTP")
public class TwoFactorController {

    private final TwoFactorService twoFactor;
    private final AuthLoginService auth;
    private final SessionCookieService cookies;

    public TwoFactorController(
            TwoFactorService twoFactor,
            AuthLoginService auth,
            SessionCookieService cookies) {
        this.twoFactor = twoFactor;
        this.auth = auth;
        this.cookies = cookies;
    }

    @GetMapping("me/two-factor")
    @Operation(summary = "Xem trạng thái xác thực hai yếu tố", description = "Kiểm tra tài khoản hiện tại đã bật xác thực hai yếu tố qua email hay chưa.")
    @ApiResponse(responseCode = "200", description = "Trả về trạng thái bật/tắt của tài khoản")
    public TwoFactorStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return new TwoFactorStatusResponse(twoFactor.isEnabled(jwt.getSubject()));
    }

    @PostMapping("me/two-factor/enable")
    @Operation(summary = "Bật xác thực hai yếu tố", description = "Xác minh mật khẩu rồi gửi mã OTP 6 số về email của tài khoản; dùng challengeId trả về để gọi confirm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tạo thử thách xác thực và gửi mã qua email"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu không đúng"),
            @ApiResponse(responseCode = "409", description = "Tài khoản đã bật xác thực hai yếu tố"),
            @ApiResponse(responseCode = "502", description = "Không gửi được email mã xác thực")
    })
    public ChallengeResponse enable(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EnableTwoFactorRequest request) {
        String challengeId = twoFactor.startEnableChallenge(jwt.getSubject(), request.password());
        return new ChallengeResponse(challengeId);
    }

    @PostMapping("me/two-factor/confirm")
    @Operation(summary = "Xác nhận bật xác thực hai yếu tố", description = "Nhập mã OTP 6 số đã nhận qua email để kích hoạt chính thức; sai mã quá 5 lần thử thách bị khoá.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác thực hai yếu tố đã được bật"),
            @ApiResponse(responseCode = "400", description = "Mã không đúng, hết hạn, thử thách bị khoá hoặc không hợp lệ")
    })
    public TwoFactorStatusResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmTwoFactorRequest request) {
        twoFactor.confirmEnable(jwt.getSubject(), request.challengeId(), request.code());
        return new TwoFactorStatusResponse(true);
    }

    @PostMapping("me/two-factor/disable")
    @Operation(summary = "Tắt xác thực hai yếu tố", description = "Xác minh mật khẩu rồi tắt xác thực hai yếu tố; đăng nhập sau đó trở về một bước như cũ.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác thực hai yếu tố đã được tắt"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu không đúng")
    })
    public TwoFactorStatusResponse disable(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisableTwoFactorRequest request) {
        twoFactor.disable(jwt.getSubject(), request.password());
        return new TwoFactorStatusResponse(false);
    }

    @PostMapping("auth/two-factor/verify")
    @Operation(summary = "Hoàn tất đăng nhập bằng mã OTP", description = "Bước hai của đăng nhập khi tài khoản bật xác thực hai yếu tố: xác minh mã OTP rồi phát hành Access/Refresh Token như đăng nhập thường.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, phát hành token"),
            @ApiResponse(responseCode = "400", description = "Mã không đúng, hết hạn, thử thách bị khoá hoặc không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Tài khoản không ở trạng thái đăng nhập được"),
            @ApiResponse(responseCode = "429", description = "Vượt quá số lần thử cho phép")
    })
    public LoginResponse verify(
            @Valid @RequestBody VerifyTwoFactorLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LoginResult result = auth.completeLogin(
                request.challengeId(),
                request.code(),
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
