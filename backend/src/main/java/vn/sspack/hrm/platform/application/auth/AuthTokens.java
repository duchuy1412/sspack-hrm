package vn.sspack.hrm.platform.application.auth;

import java.time.OffsetDateTime;

/**
 * Kết quả đăng nhập hoặc làm mới: access token trả trong body, refresh token (chuỗi gốc) đặt vào cookie.
 */
public record AuthTokens(
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt,
        boolean rememberMe) {

    @Override
    public String toString() {
        // Không để token lọt vào log
        return "AuthTokens[accessTokenExpiresAt=" + accessTokenExpiresAt + ", rememberMe=" + rememberMe + "]";
    }
}
