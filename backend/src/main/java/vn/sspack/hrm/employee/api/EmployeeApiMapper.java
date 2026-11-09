package vn.sspack.hrm.employee.api;

import org.mapstruct.Mapper;

import vn.sspack.hrm.employee.api.EmployeeDtos.CreateEmployeeRequest;
import vn.sspack.hrm.employee.api.EmployeeDtos.EmployeeResponse;
import vn.sspack.hrm.employee.api.EmployeeDtos.EmployeeSummaryResponse;
import vn.sspack.hrm.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import vn.sspack.hrm.employee.application.EmployeeService.EmployeeDetail;
import vn.sspack.hrm.employee.application.EmployeeService.EmployeeSummary;
import vn.sspack.hrm.employee.application.EmployeeService.SaveCommand;
import vn.sspack.hrm.employee.application.EmployeeService.UpdateCommand;

/** Mapping DTO của hồ sơ nhân viên (EMP-01). */
@Mapper
public interface EmployeeApiMapper {

    EmployeeSummaryResponse toResponse(EmployeeSummary summary);

    EmployeeResponse toResponse(EmployeeDetail detail);

    SaveCommand toCommand(CreateEmployeeRequest request);

    SaveCommand toSaveCommand(UpdateEmployeeRequest request);

    default UpdateCommand toCommand(UpdateEmployeeRequest request) {
        return new UpdateCommand(toSaveCommand(request), request.version());
    }
}
