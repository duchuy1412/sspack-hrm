package vn.sspack.hrm.shared.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ trả ra API dưới dạng RFC 7807 (ProblemDetail).
 *
 * @param status     mã HTTP
 * @param code       mã lỗi ổn định cho frontend xử lý (vd {@code AUTH_INVALID_CREDENTIALS})
 * @param message    thông báo tiếng Việt hiển thị cho người dùng
 * @param properties thuộc tính bổ sung đưa vào ProblemDetail
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
