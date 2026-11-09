package vn.sspack.hrm.employee.domain;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Vi phạm quy tắc hồ sơ nhân viên (EMP-01), mã lỗi ổn định cho frontend. */
public final class EmployeeRuleException extends ApiException {

    private EmployeeRuleException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static EmployeeRuleException invalidCode() {
        return new EmployeeRuleException(HttpStatus.BAD_REQUEST, "EMPLOYEE_CODE_INVALID",
                "Mã nhân viên gồm 2–30 ký tự: chữ in hoa, chữ số, dấu gạch dưới hoặc gạch ngang.");
    }

    static EmployeeRuleException nameRequired() {
        return new EmployeeRuleException(HttpStatus.BAD_REQUEST, "EMPLOYEE_NAME_REQUIRED",
                "Vui lòng nhập họ và tên (tối đa 150 ký tự).");
    }

    static EmployeeRuleException birthDateInvalid() {
        return new EmployeeRuleException(HttpStatus.BAD_REQUEST, "EMPLOYEE_BIRTH_DATE_INVALID",
                "Ngày sinh phải trước ngày vào làm và không ở tương lai.");
    }

    static EmployeeRuleException phoneInvalid() {
        return new EmployeeRuleException(HttpStatus.BAD_REQUEST, "EMPLOYEE_PHONE_INVALID",
                "Số điện thoại gồm 9–15 chữ số, có thể bắt đầu bằng dấu +.");
    }

    static EmployeeRuleException statusNotAllowed() {
        return new EmployeeRuleException(HttpStatus.BAD_REQUEST, "EMPLOYEE_STATUS_NOT_ALLOWED",
                "Không chọn được trạng thái này. Nghỉ việc được xử lý ở chức năng nghỉ việc.");
    }

    static EmployeeRuleException departmentInactive() {
        return new EmployeeRuleException(HttpStatus.CONFLICT, "EMPLOYEE_DEPARTMENT_INACTIVE",
                "Phòng ban đã ngừng hoạt động. Hãy chọn phòng ban khác.");
    }

    static EmployeeRuleException positionInactive() {
        return new EmployeeRuleException(HttpStatus.CONFLICT, "EMPLOYEE_POSITION_INACTIVE",
                "Chức danh đã ngừng hoạt động. Hãy chọn chức danh khác.");
    }
}
