package vn.sspack.hrm.org.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Vi phạm quy tắc của chức danh (ORG-02), mã lỗi ổn định cho frontend. */
public final class PositionRuleException extends ApiException {

    private PositionRuleException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(status, code, message, properties);
    }

    private PositionRuleException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    static PositionRuleException invalidCode() {
        return new PositionRuleException(HttpStatus.BAD_REQUEST, "POSITION_CODE_INVALID",
                "Mã chức danh gồm 2–30 ký tự: chữ in hoa, chữ số, dấu gạch dưới hoặc gạch ngang.");
    }

    static PositionRuleException gradeRequired() {
        return new PositionRuleException(HttpStatus.BAD_REQUEST, "POSITION_GRADE_REQUIRED",
                "Chức danh phải thuộc một cấp bậc của công ty.");
    }

    static PositionRuleException gradeInactive() {
        return new PositionRuleException(HttpStatus.CONFLICT, "POSITION_GRADE_INACTIVE",
                "Cấp bậc đã ngừng hoạt động. Hãy chọn cấp bậc khác hoặc cho cấp bậc hoạt động lại.");
    }

    static PositionRuleException hasEmployees(long count) {
        return new PositionRuleException(HttpStatus.CONFLICT, "POSITION_HAS_EMPLOYEES",
                "Chức danh đã có " + count + " nhân viên nên không xóa được. Hãy chọn Ngừng hoạt động.",
                Map.of("employeeCount", count));
    }
}
