package vn.sspack.hrm.platform.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.sspack.hrm.shared.error.ApiException;
import vn.sspack.hrm.shared.security.DataScope;

/** PLT-03: quy tắc nghiệp vụ của vai trò và gán vai trò cho tài khoản. */
class RoleRulesTest {

    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 7, 9, 0, 0, 0, ZoneOffset.ofHours(7));
    static final Permission LEAVE_READ = Permission.of("leave:read", "leave", "Xem đơn nghỉ");
    static final Permission LEAVE_APPROVE = Permission.of("leave:approve", "leave", "Duyệt đơn nghỉ");
    static final Permission ROLE_MANAGE = Permission.of("role:manage", "platform", "Quản lý vai trò");
    static final Permission USER_MANAGE = Permission.of("user:manage", "platform", "Quản lý tài khoản");

    static Role customRole(String code, Permission... permissions) {
        return Role.create(1L, code, "Vai trò " + code, null, DataScope.DEPARTMENT, List.of(permissions), T0);
    }

    static Role systemRole(String code, DataScope scope, Permission... permissions) {
        Role role = customRole(code, permissions);
        role.update(role.getName(), null, scope, List.of(permissions), T0);
        ReflectionTestUtils.setField(role, "system", true);
        return role;
    }

    static String codeOf(ThrowingRunnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.code();
        }
        throw new AssertionError("Không ném lỗi nghiệp vụ");
    }

    interface ThrowingRunnable {
        void run();
    }

    @Nested
    @DisplayName("Tạo vai trò")
    class Create {

        @Test
        @DisplayName("Mã được chuẩn hóa về chữ in hoa, tên và mô tả được cắt khoảng trắng")
        void normalizesCodeAndText() {
            Role role = Role.create(1L, " team_lead ", "  Trưởng nhóm ", "  ", DataScope.DEPARTMENT,
                    List.of(LEAVE_READ), T0);

            assertThat(role.getCode()).isEqualTo("TEAM_LEAD");
            assertThat(role.getName()).isEqualTo("Trưởng nhóm");
            assertThat(role.getDescription()).isNull();
            assertThat(role.isSystem()).isFalse();
            assertThat(role.getUpdatedAt()).isEqualTo(T0);
            assertThat(role.permissionCodes()).containsExactly("leave:read");
        }

        @Test
        @DisplayName("Mã sai định dạng bị từ chối")
        void rejectsInvalidCode() {
            for (String code : new String[] {"A", "1ABC", "TEAM-LEAD", "TRƯỞNG", "", null, "A".repeat(51)}) {
                assertThat(codeOf(() -> customRole(code, LEAVE_READ))).as(String.valueOf(code))
                        .isEqualTo("ROLE_CODE_INVALID");
            }
        }

        @Test
        @DisplayName("Vai trò không có quyền nào bị từ chối")
        void requiresAtLeastOnePermission() {
            assertThat(codeOf(() -> customRole("EMPTY"))).isEqualTo("ROLE_NO_PERMISSION");
        }
    }

    @Nested
    @DisplayName("Sửa vai trò")
    class Update {

        @Test
        @DisplayName("Thay toàn bộ danh sách quyền và phạm vi dữ liệu")
        void replacesPermissions() {
            Role role = customRole("TEAM_LEAD", LEAVE_READ);

            role.update("Trưởng nhóm", "Duyệt đơn của nhóm", DataScope.DEPARTMENT_TREE, List.of(LEAVE_APPROVE),
                    T0.plusHours(1));

            assertThat(role.permissionCodes()).containsExactly("leave:approve");
            assertThat(role.getDataScope()).isEqualTo(DataScope.DEPARTMENT_TREE);
            assertThat(role.getDescription()).isEqualTo("Duyệt đơn của nhóm");
            assertThat(role.getUpdatedAt()).isEqualTo(T0.plusHours(1));
        }

        @Test
        @DisplayName("Không bỏ hết quyền khi sửa")
        void cannotRemoveAllPermissions() {
            Role role = customRole("TEAM_LEAD", LEAVE_READ);

            assertThat(codeOf(() -> role.update("X", null, DataScope.SELF, List.of(), T0)))
                    .isEqualTo("ROLE_NO_PERMISSION");
            assertThat(role.permissionCodes()).containsExactly("leave:read");
        }

        @Test
        @DisplayName("Vai trò hệ thống: sửa được quyền nhưng không đổi được phạm vi dữ liệu")
        void systemRoleScopeIsLocked() {
            Role manager = systemRole("MANAGER", DataScope.DEPARTMENT_TREE, LEAVE_READ);

            manager.update("Quản lý", null, DataScope.DEPARTMENT_TREE, List.of(LEAVE_READ, LEAVE_APPROVE), T0);
            assertThat(manager.permissionCodes()).containsExactly("leave:approve", "leave:read");

            assertThat(codeOf(() -> manager.update("Quản lý", null, DataScope.ALL, List.of(LEAVE_READ), T0)))
                    .isEqualTo("ROLE_SYSTEM_SCOPE_LOCKED");
        }

        @Test
        @DisplayName("Vai trò ADMIN phải giữ quyền role:manage")
        void adminKeepsRoleManage() {
            Role admin = systemRole("ADMIN", DataScope.ALL, ROLE_MANAGE, USER_MANAGE);

            assertThat(codeOf(() -> admin.update("Quản trị", null, DataScope.ALL, List.of(USER_MANAGE), T0)))
                    .isEqualTo("ROLE_ADMIN_MUST_MANAGE_ROLES");
            assertThat(admin.grants("role:manage")).isTrue();
        }
    }

    @Nested
    @DisplayName("Xóa vai trò")
    class Delete {

        @Test
        @DisplayName("Vai trò tùy chỉnh chưa gán cho ai: xóa được")
        void unassignedCustomRoleIsDeletable() {
            customRole("TEAM_LEAD", LEAVE_READ).ensureDeletable(0);
        }

        @Test
        @DisplayName("Vai trò hệ thống không xóa được")
        void systemRoleIsNotDeletable() {
            Role hr = systemRole("HR", DataScope.ALL, LEAVE_READ);

            assertThat(codeOf(() -> hr.ensureDeletable(0))).isEqualTo("ROLE_SYSTEM_NOT_DELETABLE");
        }

        @Test
        @DisplayName("Vai trò đang gán không xóa được, lỗi kèm số người dùng")
        void assignedRoleIsNotDeletable() {
            Role role = customRole("TEAM_LEAD", LEAVE_READ);

            assertThatThrownBy(() -> role.ensureDeletable(3))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.code()).isEqualTo("ROLE_IN_USE");
                        assertThat(e.properties()).containsEntry("assignedUserCount", 3L);
                    });
        }
    }

    @Nested
    @DisplayName("Gán vai trò cho tài khoản")
    class Assign {

        final Role admin = systemRole("ADMIN", DataScope.ALL, ROLE_MANAGE, USER_MANAGE);
        final Role employee = systemRole("EMPLOYEE", DataScope.SELF, LEAVE_READ);

        @Test
        @DisplayName("Thay toàn bộ vai trò")
        void replacesRoles() {
            UserAccount user = UserAccount.create(1L, "vanduc.pham@sspack.vn", "hash", T0);
            user.assignRoles(List.of(employee), false, T0);

            user.assignRoles(List.of(admin, employee), false, T0.plusMinutes(1));

            assertThat(user.roleCodes()).containsExactly("ADMIN", "EMPLOYEE");
            assertThat(user.permissionCodes()).contains("user:manage", "leave:read");
        }

        @Test
        @DisplayName("Phải còn ít nhất một vai trò")
        void requiresAtLeastOneRole() {
            UserAccount user = UserAccount.create(1L, "vanduc.pham@sspack.vn", "hash", T0);

            assertThat(codeOf(() -> user.assignRoles(List.of(), false, T0))).isEqualTo("USER_NO_ROLE");
        }

        @Test
        @DisplayName("Admin không tự gỡ quyền quản lý tài khoản của mình, nhưng gỡ được cho người khác")
        void cannotLockSelfOut() {
            UserAccount self = UserAccount.create(1L, "admin@sspack.vn", "hash", T0);
            self.assignRoles(List.of(admin), false, T0);

            assertThat(codeOf(() -> self.assignRoles(List.of(employee), true, T0))).isEqualTo("USER_SELF_LOCKOUT");
            assertThat(self.roleCodes()).containsExactly("ADMIN");

            self.assignRoles(List.of(admin, employee), true, T0);
            assertThat(self.roleCodes()).containsExactly("ADMIN", "EMPLOYEE");

            UserAccount other = UserAccount.create(1L, "admin2@sspack.vn", "hash", T0);
            other.assignRoles(List.of(admin), false, T0);
            other.assignRoles(List.of(employee), false, T0);
            assertThat(other.roleCodes()).containsExactly("EMPLOYEE");
        }
    }
}
