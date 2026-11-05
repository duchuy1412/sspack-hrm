package vn.sspack.hrm.platform.infrastructure.security;

import java.time.OffsetDateTime;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import vn.sspack.hrm.platform.application.auth.AccessTokenIssuer;
import vn.sspack.hrm.platform.application.auth.AuthProperties;
import vn.sspack.hrm.platform.domain.auth.UserAccount;

/**
 * Phát hành access token JWT HS256. Token mang vai trò và quyền để {@code @PreAuthorize} kiểm tra mà
 * không cần truy vấn CSDL ở mỗi request; quyền thay đổi có hiệu lực ở lần làm mới kế tiếp (≤ 15 phút).
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_COMPANY = "company_id";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMISSIONS = "permissions";

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public JwtAccessTokenIssuer(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(UserAccount user, OffsetDateTime now) {
        OffsetDateTime expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now.toInstant())
                .expiresAt(expiresAt.toInstant())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_COMPANY, user.getCompanyId())
                .claim(CLAIM_ROLES, user.roleCodes())
                .claim(CLAIM_PERMISSIONS, user.permissionCodes())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
