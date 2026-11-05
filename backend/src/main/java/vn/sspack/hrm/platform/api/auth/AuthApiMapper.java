package vn.sspack.hrm.platform.api.auth;

import org.mapstruct.Mapper;

import vn.sspack.hrm.platform.api.auth.AuthDtos.MeResponse;
import vn.sspack.hrm.platform.api.auth.AuthDtos.RoleResponse;
import vn.sspack.hrm.platform.application.auth.CurrentUser;

@Mapper
public interface AuthApiMapper {

    MeResponse toResponse(CurrentUser user);

    RoleResponse toResponse(CurrentUser.RoleInfo role);
}
