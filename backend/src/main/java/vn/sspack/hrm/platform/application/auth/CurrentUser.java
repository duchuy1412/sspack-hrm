package vn.sspack.hrm.platform.application.auth;

import java.util.List;
import java.util.Set;

import vn.sspack.hrm.shared.security.DataScope;

/** Thông tin người đang đăng nhập: vai trò kèm phạm vi dữ liệu và tập quyền. */
public record CurrentUser(Long id, String email, String preferredLocale, List<RoleInfo> roles, Set<String> permissions) {

    public record RoleInfo(String code, String name, DataScope dataScope) {
    }
}
