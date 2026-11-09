package vn.sspack.hrm.org.api;

import org.mapstruct.Mapper;

import vn.sspack.hrm.org.api.JobGradeDtos.CreateJobGradeRequest;
import vn.sspack.hrm.org.api.JobGradeDtos.JobGradeResponse;
import vn.sspack.hrm.org.api.JobGradeDtos.JobGradeStatusRequest;
import vn.sspack.hrm.org.api.JobGradeDtos.UpdateJobGradeRequest;
import vn.sspack.hrm.org.api.PositionDtos.CreatePositionRequest;
import vn.sspack.hrm.org.api.PositionDtos.PositionResponse;
import vn.sspack.hrm.org.api.PositionDtos.PositionStatusRequest;
import vn.sspack.hrm.org.api.PositionDtos.UpdatePositionRequest;
import vn.sspack.hrm.org.application.JobGradeService;
import vn.sspack.hrm.org.application.JobGradeService.JobGradeView;
import vn.sspack.hrm.org.application.PositionService;
import vn.sspack.hrm.org.application.PositionService.PositionView;

/** Mapping DTO của danh mục cấp bậc và chức danh (ORG-02). */
@Mapper
public interface OrgCatalogApiMapper {

    JobGradeResponse toResponse(JobGradeView view);

    JobGradeService.SaveCommand toCommand(CreateJobGradeRequest request);

    JobGradeService.UpdateCommand toCommand(UpdateJobGradeRequest request);

    JobGradeService.StatusCommand toCommand(JobGradeStatusRequest request);

    PositionResponse toResponse(PositionView view);

    PositionService.SaveCommand toCommand(CreatePositionRequest request);

    PositionService.UpdateCommand toCommand(UpdatePositionRequest request);

    PositionService.StatusCommand toCommand(PositionStatusRequest request);
}
