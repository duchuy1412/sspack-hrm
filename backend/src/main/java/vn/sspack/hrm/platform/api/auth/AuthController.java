package vn.sspack.hrm.platform.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.platform.api.auth.AuthDtos.AccessTokenResponse;
import vn.sspack.hrm.platform.api.auth.AuthDtos.LoginRequest;
import vn.sspack.hrm.platform.api.auth.AuthDtos.MeResponse;
import vn.sspack.hrm.platform.application.auth.AuthProperties;
import vn.sspack.hrm.platform.application.auth.AuthService;
import vn.sspack.hrm.platform.application.auth.AuthService.LoginCommand;
import vn.sspack.hrm.platform.application.auth.AuthTokens;

/** PLT-01: đăng nhập, làm mới phiên, đăng xuất, xem người đang đăng nhập. */
@Tag(name = "Xác thực", description = "PLT-01: đăng nhập bằng email/mật khẩu với JWT")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthApiMapper mapper;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthController(AuthService authService, AuthApiMapper mapper, AuthProperties properties, Clock clock) {
        this.authService = authService;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Operation(summary = "Đăng nhập",
            description = "Trả access token (15 phút) và đặt refresh token (7 ngày) vào cookie HttpOnly. "
                    + "Sai mật khẩu 5 lần liên tiếp thì tài khoản bị khóa 15 phút (HTTP 423).")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest http) {
        AuthTokens tokens = authService.login(new LoginCommand(
                request.email(), request.password(), request.rememberMe(), http.getRemoteAddr()));
        return withTokens(tokens);
    }

    @Operation(summary = "Làm mới phiên",
            description = "Đổi refresh token trong cookie lấy access token mới; refresh token được xoay vòng.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "${hrm.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest http) {
        return withTokens(authService.refresh(refreshToken, http.getRemoteAddr()));
    }

    @Operation(summary = "Đăng xuất", description = "Thu hồi refresh token hiện tại và xóa cookie.")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${hrm.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest http) {
        authService.logout(refreshToken, http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @Operation(summary = "Người đang đăng nhập", description = "Vai trò, phạm vi dữ liệu và quyền.")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(authService.currentUser(Long.valueOf(jwt.getSubject())));
    }

    private ResponseEntity<AccessTokenResponse> withTokens(AuthTokens tokens) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Duration cookieMaxAge = tokens.rememberMe()
                ? Duration.between(now, tokens.refreshTokenExpiresAt())
                : null; // cookie phiên: mất khi đóng trình duyệt
        long expiresIn = Math.max(0, Duration.between(now, tokens.accessTokenExpiresAt()).toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), cookieMaxAge).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AccessTokenResponse(tokens.accessToken(), "Bearer", expiresIn));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        AuthProperties.Cookie cookie = properties.cookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Strict")
                .path(cookie.path());
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }
}
