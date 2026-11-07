package vn.sspack.hrm.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Người thực hiện thao tác, lấy từ access token: công ty, tài khoản, IP. Dùng cho audit log và lọc theo công ty.
 */
public record RequestActor(long companyId, long userId, String ipAddress) {

    /** Claim công ty trong access token (đặt bởi module platform khi phát hành token). */
    public static final String CLAIM_COMPANY = "company_id";

    public static RequestActor of(Jwt jwt, HttpServletRequest request) {
        return new RequestActor(companyId(jwt), Long.parseLong(jwt.getSubject()), request.getRemoteAddr());
    }

    public static long companyId(Jwt jwt) {
        Object value = jwt.getClaim(CLAIM_COMPANY);
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
