package vn.sspack.hrm.platform.api.rbac;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import vn.sspack.hrm.platform.domain.auth.DataScope;

/** DTO của API phân quyền (PLT-03). */
public final class RbacDtos {

    private RbacDtos() {
    }

    @Schema(description = "Quyền theo chức năng")
    public record PermissionResponse(
            @Schema(example = "leave:approve") String code,
            @Schema(example = "leave") String module,
            String description) {
    }

    @Schema(description = "Vai trò trong danh sách")
    public record RoleSummaryResponse(Long id, String code, String name, String description, DataScope dataScope,
                                      @Schema(description = "Vai trò hệ thống: không xóa được") boolean system,
                                      int permissionCount, long assignedUserCount, OffsetDateTime updatedAt) {
    }

    @Schema(description = "Chi tiết vai trò")
    public record RoleResponse(Long id, String code, String name, String description, DataScope dataScope,
                               boolean system, Set<String> permissions, long assignedUserCount,
                               @Schema(description = "Gửi lại khi sửa để phát hiện sửa trùng") int version,
                               OffsetDateTime updatedAt) {
    }

    @Schema(description = "Tạo vai trò")
    public record CreateRoleRequest(
            @NotBlank(message = "Vui lòng nhập mã vai trò")
            @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}",
                    message = "Mã gồm 2–50 ký tự: chữ, số hoặc dấu gạch dưới, bắt đầu bằng chữ cái")
            @Schema(example = "TEAM_LEAD") String code,

            @NotBlank(message = "Vui lòng nhập tên vai trò")
            @Size(max = 100, message = "Tên vai trò tối đa 100 ký tự") String name,

            @Size(max = 255, message = "Mô tả tối đa 255 ký tự") String description,

            @NotNull(message = "Vui lòng chọn phạm vi dữ liệu") DataScope dataScope,

            @NotEmpty(message = "Vui lòng chọn ít nhất một quyền") Set<String> permissions) {
    }

    @Schema(description = "Sửa vai trò; danh sách quyền thay thế toàn bộ quyền cũ")
    public record UpdateRoleRequest(
            @NotBlank(message = "Vui lòng nhập tên vai trò")
            @Size(max = 100, message = "Tên vai trò tối đa 100 ký tự") String name,

            @Size(max = 255, message = "Mô tả tối đa 255 ký tự") String description,

            @NotNull(message = "Vui lòng chọn phạm vi dữ liệu") DataScope dataScope,

            @NotEmpty(message = "Vui lòng chọn ít nhất một quyền") Set<String> permissions,

            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    public record RoleRefResponse(Long id, String code, String name) {
    }

    @Schema(description = "Tài khoản kèm vai trò")
    public record UserRolesResponse(Long id, String email, String status, List<RoleRefResponse> roles, int version) {
    }

    @Schema(description = "Thay toàn bộ vai trò của tài khoản")
    public record AssignRolesRequest(
            @NotEmpty(message = "Vui lòng chọn ít nhất một vai trò") Set<Long> roleIds,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }
}
