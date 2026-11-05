package vn.sspack.hrm.platform.domain.auth;

import java.time.Duration;
import java.util.Objects;

/**
 * Quy tắc khóa tài khoản khi đăng nhập sai (PLT-01): sai {@code maxFailedAttempts} lần liên tiếp thì khóa
 * trong {@code lockDuration}. Đây là quy định nội bộ, cấu hình qua {@code hrm.auth.*}.
 */
public record LoginLockPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LoginLockPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts phải ≥ 1");
        }
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration phải > 0");
        }
    }
}
