package vn.sspack.hrm.platform.application.auth;

import java.time.OffsetDateTime;

import vn.sspack.hrm.platform.domain.auth.UserAccount;

/** Cổng phát hành access token; hiện thực bằng JWT ở tầng hạ tầng. */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(UserAccount user, OffsetDateTime now);

    record IssuedAccessToken(String value, OffsetDateTime expiresAt) {
    }
}
