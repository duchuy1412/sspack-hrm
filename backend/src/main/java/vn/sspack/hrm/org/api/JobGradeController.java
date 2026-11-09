package vn.sspack.hrm.org.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.org.api.JobGradeDtos.CreateJobGradeRequest;
import vn.sspack.hrm.org.api.JobGradeDtos.JobGradeResponse;
import vn.sspack.hrm.org.api.JobGradeDtos.JobGradeStatusRequest;
import vn.sspack.hrm.org.api.JobGradeDtos.UpdateJobGradeRequest;
import vn.sspack.hrm.org.application.JobGradeService;
import vn.sspack.hrm.shared.api.RequestActor;

/**
 * ORG-02: cấp bậc và khung lương. Khung lương là dữ liệu nhạy cảm nên cả đọc lẫn ghi đều cần
 * {@code org:manage}.
 */
@Tag(name = "Cơ cấu tổ chức", description = "ORG-02: cấp bậc và khung lương")
@RestController
@RequestMapping("/api/v1/job-grades")
@PreAuthorize("hasAuthority('org:manage')")
public class JobGradeController {

    private final JobGradeService service;
    private final OrgCatalogApiMapper mapper;

    public JobGradeController(JobGradeService service, OrgCatalogApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh sách cấp bậc",
            description = "Xếp theo khung lương tăng dần; không phân trang vì danh mục nhỏ và dùng cho ô chọn.")
    @GetMapping
    public List<JobGradeResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(RequestActor.companyId(jwt), includeInactive).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Chi tiết cấp bậc")
    @GetMapping("/{id}")
    public JobGradeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return mapper.toResponse(service.get(RequestActor.companyId(jwt), id));
    }

    @Operation(summary = "Tạo cấp bậc", description = "Mã được chuyển về chữ in hoa; lương tối thiểu ≤ tối đa.")
    @PostMapping
    public ResponseEntity<JobGradeResponse> create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                   @Valid @RequestBody CreateJobGradeRequest request) {
        JobGradeResponse body = mapper.toResponse(service.create(RequestActor.of(jwt, http), mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Sửa cấp bậc và khung lương")
    @PutMapping("/{id}")
    public JobGradeResponse update(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                                   @Valid @RequestBody UpdateJobGradeRequest request) {
        return mapper.toResponse(service.update(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Ngừng hoạt động / hoạt động lại",
            description = "Ngừng hoạt động khi không còn chức danh đang hoạt động thuộc cấp bậc.")
    @PutMapping("/{id}/status")
    public JobGradeResponse changeStatus(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                         @PathVariable long id, @Valid @RequestBody JobGradeStatusRequest request) {
        return mapper.toResponse(service.changeStatus(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Xóa cấp bậc", description = "Chỉ khi chưa có chức danh nào thuộc cấp bậc.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                       @PathVariable long id) {
        service.delete(RequestActor.of(jwt, http), id);
        return ResponseEntity.noContent().build();
    }
}
