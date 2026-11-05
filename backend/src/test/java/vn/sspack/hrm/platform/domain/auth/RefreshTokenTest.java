package vn.sspack.hrm.platform.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 5, 8, 0, 0, 0, ZoneOffset.ofHours(7));
    static final Duration TTL = Duration.ofDays(7);
    static final Duration GRACE = Duration.ofSeconds(30);

    @Test
    void expiresAfterSevenDays() {
        RefreshToken token = RefreshToken.issue(1L, "hash", true, T0, TTL);

        assertThat(token.getExpiresAt()).isEqualTo(T0.plusDays(7));
        assertThat(token.isExpired(T0.plusDays(7).minusSeconds(1))).isFalse();
        assertThat(token.isExpired(T0.plusDays(7))).isTrue();
        assertThat(token.isRememberMe()).isTrue();
    }

    @Test
    void revokeKeepsFirstRevocationTime() {
        RefreshToken token = RefreshToken.issue(1L, "hash", false, T0, TTL);

        token.revoke(T0.plusMinutes(1));
        token.revoke(T0.plusMinutes(5));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(T0.plusMinutes(1));
    }

    @Test
    void reuseWithinGraceIsNotTreatedAsTheft() {
        RefreshToken token = RefreshToken.issue(1L, "hash", false, T0, TTL);
        token.revoke(T0);

        assertThat(token.isReuseBeyondGrace(T0.plusSeconds(30), GRACE)).isFalse();
        assertThat(token.isReuseBeyondGrace(T0.plusSeconds(31), GRACE)).isTrue();
    }

    @Test
    void activeTokenIsNeverReuse() {
        RefreshToken token = RefreshToken.issue(1L, "hash", false, T0, TTL);

        assertThat(token.isReuseBeyondGrace(T0.plusDays(1), GRACE)).isFalse();
    }
}
