package vn.sspack.hrm.platform.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PLT-01: khóa tài khoản 15 phút sau 5 lần đăng nhập sai liên tiếp. */
class UserAccountLockTest {

    static final LoginLockPolicy POLICY = new LoginLockPolicy(5, Duration.ofMinutes(15));
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 5, 8, 0, 0, 0, ZoneOffset.ofHours(7));

    UserAccount user;

    @BeforeEach
    void setUp() {
        user = UserAccount.create(1L, "vanduc.pham@sspack.vn", "hash", T0);
    }

    @Test
    @DisplayName("4 lần sai: chưa khóa, bộ đếm = 4")
    void fourFailuresDoNotLock() {
        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedLogin(T0.plusSeconds(i), POLICY)).isFalse();
        }
        assertThat(user.getFailedLoginCount()).isEqualTo(4);
        assertThat(user.isTemporarilyLocked(T0.plusSeconds(5))).isFalse();
    }

    @Test
    @DisplayName("Lần sai thứ 5: khóa 15 phút tính từ lần sai đó và đặt lại bộ đếm")
    void fifthFailureLocksForFifteenMinutes() {
        for (int i = 0; i < 4; i++) {
            user.registerFailedLogin(T0, POLICY);
        }
        OffsetDateTime fifth = T0.plusMinutes(2);

        assertThat(user.registerFailedLogin(fifth, POLICY)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(fifth.plusMinutes(15));
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.isTemporarilyLocked(fifth.plusMinutes(14).plusSeconds(59))).isTrue();
        assertThat(user.remainingLock(fifth.plusMinutes(10))).contains(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Hết 15 phút: tự mở khóa, lại có đủ 5 lần thử")
    void lockExpiresAndCountRestarts() {
        for (int i = 0; i < 5; i++) {
            user.registerFailedLogin(T0, POLICY);
        }
        OffsetDateTime afterLock = T0.plusMinutes(15);

        assertThat(user.isTemporarilyLocked(afterLock)).isFalse();
        assertThat(user.remainingLock(afterLock)).isEmpty();
        assertThat(user.registerFailedLogin(afterLock, POLICY)).isFalse();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Đăng nhập đúng: xóa bộ đếm sai và ghi thời điểm đăng nhập")
    void successResetsCounter() {
        user.registerFailedLogin(T0, POLICY);
        user.registerFailedLogin(T0, POLICY);

        user.registerSuccessfulLogin(T0.plusMinutes(1));

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(T0.plusMinutes(1));
    }

    @Test
    void newAccountIsEnabled() {
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void policyRejectsInvalidValues() {
        assertThatThrownBy(() -> new LoginLockPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginLockPolicy(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
