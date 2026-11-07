package vn.sspack.hrm.org.api;

import org.mapstruct.Mapper;

import vn.sspack.hrm.org.api.DepartmentDtos.CreateDepartmentRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.DepartmentResponse;
import vn.sspack.hrm.org.api.DepartmentDtos.DepartmentStatusRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.MoveDepartmentRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.UpdateDepartmentRequest;
import vn.sspack.hrm.org.application.DepartmentService.CreateCommand;
import vn.sspack.hrm.org.application.DepartmentService.DepartmentView;
import vn.sspack.hrm.org.application.DepartmentService.MoveCommand;
import vn.sspack.hrm.org.application.DepartmentService.StatusCommand;
import vn.sspack.hrm.org.application.DepartmentService.UpdateCommand;

@Mapper
public interface DepartmentApiMapper {

    DepartmentResponse toResponse(DepartmentView view);

    CreateCommand toCommand(CreateDepartmentRequest request);

    UpdateCommand toCommand(UpdateDepartmentRequest request);

    MoveCommand toCommand(MoveDepartmentRequest request);

    StatusCommand toCommand(DepartmentStatusRequest request);
}
