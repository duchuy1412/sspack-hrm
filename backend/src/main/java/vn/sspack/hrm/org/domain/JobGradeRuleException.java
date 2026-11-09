package vn.sspack.hrm.org.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Vi phạm quy tắc của cấp bậc và khung lương (ORG-02), mã lỗi ổn định cho frontend. */
public final class JobGradeRuleException extends ApiException {

    private JobGradeRuleException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(status, code, message, properties);
    }

    private JobGradeRuleException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    static JobGradeRuleException invalidCode() {
        return new JobGradeRuleException(HttpStatus.BAD_REQUEST, "JOB_GRADE_CODE_INVALID",
                "Mã cấp bậc gồm 2–20 ký tự: chữ in hoa, chữ số, dấu gạch dưới hoặc gạch ngang.");
    }

    static JobGradeRuleException salaryInvalid() {
        return new JobGradeRuleException(HttpStatus.BAD_REQUEST, "JOB_GRADE_SALARY_INVALID",
                "Mức lương phải là số đồng nguyên lớn hơn 0 và không quá 15 chữ số.");
    }

    static JobGradeRuleException rangeInverted() {
        return new JobGradeRuleException(HttpStatus.BAD_REQUEST, "JOB_GRADE_RANGE_INVALID",
                "Lương tối thiểu không được lớn hơn lương tối đa.");
    }

    static JobGradeRuleException hasPositions(long count) {
        return new JobGradeRuleException(HttpStatus.CONFLICT, "JOB_GRADE_HAS_POSITIONS",
                "Cấp bậc đang có " + count + " chức danh nên không xóa được. Hãy chọn Ngừng hoạt động.",
                Map.of("positionCount", count));
    }

    static JobGradeRuleException hasActivePositions(long count) {
        return new JobGradeRuleException(HttpStatus.CONFLICT, "JOB_GRADE_HAS_ACTIVE_POSITIONS",
                "Cấp bậc còn " + count + " chức danh đang hoạt động. Hãy chuyển hoặc ngừng các chức danh đó trước.",
                Map.of("positionCount", count));
    }
}
