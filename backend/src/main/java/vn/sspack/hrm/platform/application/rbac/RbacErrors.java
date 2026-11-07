package vn.sspack.hrm.platform.application.rbac;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import vn.sspack.hrm.shared.error.ApiException;

/** Lỗi của API phân quyền với thông báo tiếng Việt và mã ổn định cho frontend. */
final class RbacErrors {

    private RbacErrors() {
    }

    static ApiException roleNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Không tìm thấy vai trò.");
    }

    static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng.");
    }

    static ApiException roleCodeExists(String code) {
        return new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS",
                "Mã vai trò " + code + " đã tồn tại.");
    }

    static ApiException unknownPermissions(Collection<String> codes) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PERMISSION_UNKNOWN",
                "Quyền không tồn tại: " + String.join(", ", codes) + ".",
                Map.of("permissions", List.copyOf(codes)));
    }

    static ApiException unknownRoles(Collection<Long> ids) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ROLE_UNKNOWN", "Có vai trò không tồn tại.",
                Map.of("roleIds", List.copyOf(ids)));
    }

    /** Client gửi {@code version} cũ: người khác đã sửa sau khi client tải dữ liệu. */
    static ApiException staleVersion() {
        return new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại và thử lại.");
    }
}
