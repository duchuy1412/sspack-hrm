package vn.sspack.hrm.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Tạo ProblemDetail thống nhất: {@code title} tiếng Việt, {@code code} ổn định cho frontend. */
public final class ProblemDetails {

    private static final String TYPE_PREFIX = "urn:sspack-hrm:problem:";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setTitle(titleFor(status));
        problem.setProperty("code", code);
        return problem;
    }

    static String titleFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Dữ liệu không hợp lệ";
            case UNAUTHORIZED -> "Chưa xác thực";
            case FORBIDDEN -> "Không có quyền";
            case NOT_FOUND -> "Không tìm thấy";
            case CONFLICT -> "Xung đột dữ liệu";
            case LOCKED -> "Tài khoản bị khóa";
            default -> status.is5xxServerError() ? "Lỗi hệ thống" : status.getReasonPhrase();
        };
    }
}
