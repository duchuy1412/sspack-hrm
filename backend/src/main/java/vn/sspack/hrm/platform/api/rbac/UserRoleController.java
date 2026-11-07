package vn.sspack.hrm.platform.api.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.platform.api.rbac.RbacDtos.AssignRolesRequest;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.UserRolesResponse;
import vn.sspack.hrm.platform.application.rbac.UserRoleService;
import vn.sspack.hrm.shared.api.PageResponse;

/** PLT-03: gán vai trò cho tài khoản. Chỉ người có {@code user:manage} (mặc định là Admin). */
@Tag(name = "Phân quyền", description = "PLT-03: tạo vai trò và gán quyền theo chức năng")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('user:manage')")
public class UserRoleController {

    private final UserRoleService userRoleService;
    private final RbacApiMapper mapper;

    public UserRoleController(UserRoleService userRoleService, RbacApiMapper mapper) {
        this.userRoleService = userRoleService;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh sách tài khoản kèm vai trò", description = "Lọc theo email chứa chuỗi q.")
    @GetMapping
    public PageResponse<UserRolesResponse> users(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestParam(required = false) String q,
                                                 @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(userRoleService.listUsers(Actors.companyId(jwt), q, pageable), mapper::toResponse);
    }

    @Operation(summary = "Gán vai trò cho tài khoản",
            description = "Thay toàn bộ vai trò. Có hiệu lực khi người dùng làm mới access token (tối đa 15 phút).")
    @PutMapping("/{id}/roles")
    public UserRolesResponse assignRoles(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                         @PathVariable long id, @Valid @RequestBody AssignRolesRequest request) {
        return mapper.toResponse(userRoleService.assignRoles(Actors.of(jwt, http), id, mapper.toCommand(request)));
    }
}
