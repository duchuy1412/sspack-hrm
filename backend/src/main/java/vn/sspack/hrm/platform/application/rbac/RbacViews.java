package vn.sspack.hrm.platform.application.rbac;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import vn.sspack.hrm.shared.security.DataScope;

/** Kết quả đọc và lệnh ghi của phân hệ phân quyền (PLT-03). Không lộ entity ra ngoài application. */
public final class RbacViews {

    private RbacViews() {
    }

    /** Người thực hiện thao tác, lấy từ access token. */
    public record Actor(long companyId, long userId, String ipAddress) {
    }

    public record PermissionView(String code, String module, String description) {
    }

    public record RoleSummary(Long id, String code, String name, String description, DataScope dataScope,
                              boolean system, int permissionCount, long assignedUserCount,
                              OffsetDateTime updatedAt) {
    }

    public record RoleDetail(Long id, String code, String name, String description, DataScope dataScope,
                             boolean system, Set<String> permissions, long assignedUserCount, int version,
                             OffsetDateTime updatedAt) {
    }

    public record CreateRoleCommand(String code, String name, String description, DataScope dataScope,
                                    Set<String> permissions) {
    }

    public record UpdateRoleCommand(String name, String description, DataScope dataScope, Set<String> permissions,
                                    int version) {
    }

    public record RoleRef(Long id, String code, String name) {
    }

    public record UserRolesView(Long id, String email, String status, List<RoleRef> roles, int version) {
    }

    public record AssignRolesCommand(Set<Long> roleIds, int version) {
    }
}
