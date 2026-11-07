package vn.sspack.hrm.platform.domain.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Vi phạm quy tắc nghiệp vụ của vai trò và gán vai trò (PLT-03), mã lỗi ổn định cho frontend. */
public final class RoleRuleException extends ApiException {

    private RoleRuleException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(status, code, message, properties);
    }

    private RoleRuleException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    static RoleRuleException invalidCode() {
        return new RoleRuleException(HttpStatus.BAD_REQUEST, "ROLE_CODE_INVALID",
                "Mã vai trò gồm 2–50 ký tự: chữ in hoa, chữ số hoặc dấu gạch dưới, bắt đầu bằng chữ cái.");
    }

    static RoleRuleException noPermission() {
        return new RoleRuleException(HttpStatus.BAD_REQUEST, "ROLE_NO_PERMISSION",
                "Vai trò phải có ít nhất một quyền.");
    }

    static RoleRuleException adminMustKeepRoleManage() {
        return new RoleRuleException(HttpStatus.CONFLICT, "ROLE_ADMIN_MUST_MANAGE_ROLES",
                "Vai trò Quản trị hệ thống phải giữ quyền quản lý vai trò.");
    }

    static RoleRuleException systemRoleScopeLocked() {
        return new RoleRuleException(HttpStatus.CONFLICT, "ROLE_SYSTEM_SCOPE_LOCKED",
                "Không thể đổi phạm vi dữ liệu của vai trò hệ thống.");
    }

    static RoleRuleException systemRoleNotDeletable() {
        return new RoleRuleException(HttpStatus.CONFLICT, "ROLE_SYSTEM_NOT_DELETABLE",
                "Không thể xóa vai trò hệ thống.");
    }

    static RoleRuleException roleInUse(long assignedUserCount) {
        return new RoleRuleException(HttpStatus.CONFLICT, "ROLE_IN_USE",
                "Vai trò đang được gán cho " + assignedUserCount + " người dùng. Hãy gỡ vai trò khỏi họ trước khi xóa.",
                Map.of("assignedUserCount", assignedUserCount));
    }

    public static RoleRuleException userMustHaveRole() {
        return new RoleRuleException(HttpStatus.BAD_REQUEST, "USER_NO_ROLE",
                "Người dùng phải có ít nhất một vai trò.");
    }

    public static RoleRuleException cannotRemoveOwnUserManage() {
        return new RoleRuleException(HttpStatus.CONFLICT, "USER_SELF_LOCKOUT",
                "Bạn không thể tự gỡ vai trò đang cho phép mình quản lý tài khoản.");
    }
}
