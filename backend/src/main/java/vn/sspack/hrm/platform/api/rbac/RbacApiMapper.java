package vn.sspack.hrm.platform.api.rbac;

import org.mapstruct.Mapper;

import vn.sspack.hrm.platform.api.rbac.RbacDtos.AssignRolesRequest;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.CreateRoleRequest;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.PermissionResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.RoleRefResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.RoleResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.RoleSummaryResponse;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.UpdateRoleRequest;
import vn.sspack.hrm.platform.api.rbac.RbacDtos.UserRolesResponse;
import vn.sspack.hrm.platform.application.rbac.RbacViews.AssignRolesCommand;
import vn.sspack.hrm.platform.application.rbac.RbacViews.CreateRoleCommand;
import vn.sspack.hrm.platform.application.rbac.RbacViews.PermissionView;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleDetail;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleRef;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleSummary;
import vn.sspack.hrm.platform.application.rbac.RbacViews.UpdateRoleCommand;
import vn.sspack.hrm.platform.application.rbac.RbacViews.UserRolesView;

@Mapper
public interface RbacApiMapper {

    PermissionResponse toResponse(PermissionView view);

    RoleSummaryResponse toResponse(RoleSummary view);

    RoleResponse toResponse(RoleDetail view);

    RoleRefResponse toResponse(RoleRef view);

    UserRolesResponse toResponse(UserRolesView view);

    CreateRoleCommand toCommand(CreateRoleRequest request);

    UpdateRoleCommand toCommand(UpdateRoleRequest request);

    AssignRolesCommand toCommand(AssignRolesRequest request);
}
