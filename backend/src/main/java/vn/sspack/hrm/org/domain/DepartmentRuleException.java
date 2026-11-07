package vn.sspack.hrm.org.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Vi phạm quy tắc của cây phòng ban (ORG-01), mã lỗi ổn định cho frontend. */
public final class DepartmentRuleException extends ApiException {

    private DepartmentRuleException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(status, code, message, properties);
    }

    private DepartmentRuleException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    static DepartmentRuleException invalidCode() {
        return new DepartmentRuleException(HttpStatus.BAD_REQUEST, "DEPARTMENT_CODE_INVALID",
                "Mã phòng ban gồm 2–30 ký tự: chữ in hoa, chữ số, dấu gạch dưới hoặc gạch ngang.");
    }

    static DepartmentRuleException moveIntoOwnSubtree() {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_MOVE_CYCLE",
                "Không thể chuyển phòng ban vào chính nó hoặc vào phòng ban cấp dưới của nó.");
    }

    static DepartmentRuleException parentInactive() {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_PARENT_INACTIVE",
                "Phòng ban cấp trên đã ngừng hoạt động.");
    }

    static DepartmentRuleException hasChildren(long count) {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_HAS_CHILDREN",
                "Phòng ban còn " + count + " phòng ban cấp dưới. Hãy chuyển hoặc xóa chúng trước.",
                Map.of("childCount", count));
    }

    static DepartmentRuleException hasEmployees(long count) {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_HAS_EMPLOYEES",
                "Phòng ban đã có " + count + " nhân viên nên không xóa được. Hãy chọn Ngừng hoạt động.",
                Map.of("employeeCount", count));
    }

    static DepartmentRuleException hasActiveChildren(long count) {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_HAS_ACTIVE_CHILDREN",
                "Phòng ban còn " + count + " phòng ban cấp dưới đang hoạt động.",
                Map.of("childCount", count));
    }

    static DepartmentRuleException hasActiveEmployees(long count) {
        return new DepartmentRuleException(HttpStatus.CONFLICT, "DEPARTMENT_HAS_ACTIVE_EMPLOYEES",
                "Phòng ban còn " + count + " nhân viên đang làm việc. Hãy điều chuyển họ trước.",
                Map.of("employeeCount", count));
    }
}
