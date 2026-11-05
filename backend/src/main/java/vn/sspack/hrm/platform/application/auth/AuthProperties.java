package vn.sspack.hrm.platform.application.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import vn.sspack.hrm.platform.domain.auth.LoginLockPolicy;

/**
 * Cấu hình xác thực ({@code hrm.auth.*}). Giá trị mặc định theo tiêu chí chấp nhận PLT-01:
 * access token 15 phút, refresh token 7 ngày, khóa 15 phút sau 5 lần sai.
 */
@Validated
@ConfigurationProperties(prefix = "hrm.auth")
public record AuthProperties(
        @NotBlank(message = "Thiếu hrm.auth.jwt-secret (biến môi trường JWT_SECRET)") String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration refreshReuseGrace,
        @Min(1) int maxFailedAttempts,
        @NotNull Duration lockDuration,
        @Valid @NotNull Cookie cookie) {

    /** Cookie chứa refresh token. */
    public record Cookie(@NotBlank String name, @NotBlank String path, boolean secure) {
    }

    /** HS256 cần khóa tối thiểu 256 bit. */
    @AssertTrue(message = "hrm.auth.jwt-secret phải dài tối thiểu 32 byte")
    public boolean isJwtSecretLongEnough() {
        return jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    public LoginLockPolicy lockPolicy() {
        return new LoginLockPolicy(maxFailedAttempts, lockDuration);
    }
}
