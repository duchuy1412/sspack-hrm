package vn.sspack.hrm.platform.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import vn.sspack.hrm.platform.application.auth.AccessTokenIssuer.IssuedAccessToken;
import vn.sspack.hrm.platform.application.auth.AuthProperties;
import vn.sspack.hrm.platform.domain.auth.UserAccount;

/** Access token: HS256, sống 15 phút, mang vai trò và quyền thành authority. */
class JwtAccessTokenIssuerTest {

    static final AuthProperties PROPS = new AuthProperties(
            "unit-test-secret-unit-test-secret-0123456789", "sspack-hrm",
            Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(30), 5, Duration.ofMinutes(15),
            new AuthProperties.Cookie("hrm_refresh", "/api/v1/auth", true));

    final SecurityConfig config = new SecurityConfig();
    final JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(config.jwtEncoder(PROPS), PROPS);
    final JwtDecoder decoder = config.jwtDecoder(PROPS);

    @Test
    void issuesFifteenMinuteTokenWithIdentityClaims() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        UserAccount user = user(42L);

        IssuedAccessToken token = issuer.issue(user, now);
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(now.plusMinutes(15));
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("thuha.le@sspack.vn");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("sspack-hrm");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void permissionsAndRolesBecomeAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("1")
                .claim("email", "a@sspack.vn")
                .claim("permissions", java.util.List.of("leave:approve", "employee:read"))
                .claim("roles", java.util.List.of("MANAGER"))
                .build();

        var authorities = SecurityConfig.toAuthentication(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).containsExactlyInAnyOrder("leave:approve", "employee:read", "ROLE_MANAGER");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        AuthProperties other = new AuthProperties("another-secret-another-secret-0123456789", "sspack-hrm",
                PROPS.accessTokenTtl(), PROPS.refreshTokenTtl(), PROPS.refreshReuseGrace(), 5,
                PROPS.lockDuration(), PROPS.cookie());
        String forged = new JwtAccessTokenIssuer(config.jwtEncoder(other), other)
                .issue(user(1L), OffsetDateTime.now()).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // Bộ xác thực mặc định cho lệch đồng hồ 60 giây, nên lùi hẳn 20 phút
        String expired = issuer.issue(user(1L), OffsetDateTime.now().minusMinutes(20)).value();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsInvalid() {
        AuthProperties weak = new AuthProperties("short", "sspack-hrm", PROPS.accessTokenTtl(),
                PROPS.refreshTokenTtl(), PROPS.refreshReuseGrace(), 5, PROPS.lockDuration(), PROPS.cookie());

        assertThat(weak.isJwtSecretLongEnough()).isFalse();
        assertThat(PROPS.isJwtSecretLongEnough()).isTrue();
    }

    private static UserAccount user(long id) {
        UserAccount user = UserAccount.create(1L, "thuha.le@sspack.vn", "hash", OffsetDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
