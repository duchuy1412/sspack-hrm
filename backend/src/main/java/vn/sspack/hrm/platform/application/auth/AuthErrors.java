package vn.sspack.hrm.platform.application.auth;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Lỗi xác thực với thông báo tiếng Việt và mã ổn định cho frontend. */
final class AuthErrors {

    private AuthErrors() {
    }

    /** Dùng chung cho sai email và sai mật khẩu để không lộ email nào tồn tại. */
    static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "Email hoặc mật khẩu không đúng.");
    }

    static ApiException locked(Duration remaining, OffsetDateTime lockedUntil) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return new ApiException(HttpStatus.LOCKED, "AUTH_ACCOUNT_LOCKED",
                "Tài khoản tạm khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau " + minutes + " phút.",
                Map.of("lockedUntil", lockedUntil.toString(), "retryAfterSeconds", remaining.toSeconds()));
    }

    static ApiException disabled() {
        return new ApiException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED",
                "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị hệ thống.");
    }

    static ApiException sessionExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED",
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }
}
