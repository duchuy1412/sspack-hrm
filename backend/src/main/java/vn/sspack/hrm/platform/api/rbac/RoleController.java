package vn.sspack.hrm.platform.api.rbac;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.platform.api.rbac.RbacDtos.CreateRoleRequest;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.PermissionResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.RoleResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.RoleSummaryResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.UpdateRoleRequest;
import vn.sspack.hrm.platform.application.rbac.RoleService;
import vn.sspack.hrm.shared.api.PageResponse;

/** PLT-03: vai trò và quyền theo chức năng. Chỉ người có {@code role:manage} (mặc định là Admin). */
@Tag(name = "Phân quyền", description = "PLT-03: tạo vai trò và gán quyền theo chức năng")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('role:manage')")
public class RoleController {

    private final RoleService roleService;
    private final RbacApiMapper mapper;

    public RoleController(RoleService roleService, RbacApiMapper mapper) {
        this.roleService = roleService;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh mục quyền", description = "Sắp xếp theo phân hệ rồi theo mã quyền.")
    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return roleService.listPermissions().stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Danh sách vai trò", description = "Vai trò hệ thống lên trước, sau đó theo mã.")
    @GetMapping("/roles")
    public PageResponse<RoleSummaryResponse> roles(@AuthenticationPrincipal Jwt jwt,
                                                   @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(roleService.listRoles(Actors.companyId(jwt), pageable), mapper::toResponse);
    }

    @Operation(summary = "Chi tiết vai trò")
    @GetMapping("/roles/{id}")
    public RoleResponse role(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return mapper.toResponse(roleService.getRole(Actors.companyId(jwt), id));
    }

    @Operation(summary = "Tạo vai trò", description = "Mã vai trò được chuyển về chữ in hoa và không đổi về sau.")
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                               @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse body = mapper.toResponse(roleService.create(Actors.of(jwt, http), mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Sửa vai trò",
            description = "Thay tên, mô tả, phạm vi dữ liệu và toàn bộ danh sách quyền. Người đang đăng nhập "
                    + "nhận quyền mới khi access token được làm mới (tối đa 15 phút).")
    @PutMapping("/roles/{id}")
    public RoleResponse update(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                               @Valid @RequestBody UpdateRoleRequest request) {
        return mapper.toResponse(roleService.update(Actors.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Xóa vai trò", description = "Không xóa được vai trò hệ thống hoặc vai trò đang gán.")
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                       @PathVariable long id) {
        roleService.delete(Actors.of(jwt, http), id);
        return ResponseEntity.noContent().build();
    }
}
