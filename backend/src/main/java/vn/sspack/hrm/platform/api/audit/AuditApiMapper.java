package vn.sspack.hrm.platform.api.audit;

import org.mapstruct.Mapper;

import vn.sspack.hrm.platform.api.audit.AuditLogController.ActorResponse;
import vn.sspack.hrm.platform.api.audit.AuditLogController.AuditLogResponse;
import vn.sspack.hrm.platform.application.audit.AuditLogQueryService.ActorView;
import vn.sspack.hrm.platform.application.audit.AuditLogQueryService.AuditLogView;

@Mapper
public interface AuditApiMapper {

    AuditLogResponse toResponse(AuditLogView view);

    ActorResponse toResponse(ActorView view);
}
