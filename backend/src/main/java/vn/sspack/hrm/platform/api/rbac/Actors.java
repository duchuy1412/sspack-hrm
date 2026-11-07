package vn.sspack.hrm.platform.api.rbac;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import vn.sspack.hrm.platform.application.rbac.RbacViews.Actor;

/** Dựng người thực hiện thao tác từ access token và request. */
final class Actors {

    static final String CLAIM_COMPANY = "company_id";

    private Actors() {
    }

    static Actor of(Jwt jwt, HttpServletRequest request) {
        return new Actor(companyId(jwt), Long.parseLong(jwt.getSubject()), request.getRemoteAddr());
    }

    static long companyId(Jwt jwt) {
        Object value = jwt.getClaim(CLAIM_COMPANY);
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
