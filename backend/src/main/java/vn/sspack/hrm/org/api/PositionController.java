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

import vn.sspack.hrm.org.api.PositionDtos.CreatePositionRequest;
import vn.sspack.hrm.org.api.PositionDtos.PositionResponse;
import vn.sspack.hrm.org.api.PositionDtos.PositionStatusRequest;
import vn.sspack.hrm.org.api.PositionDtos.UpdatePositionRequest;
import vn.sspack.hrm.org.application.PositionService;
import vn.sspack.hrm.org.application.PositionService.Filter;
import vn.sspack.hrm.shared.api.RequestActor;

/** ORG-02: danh mục chức danh. Mọi người dùng xem được ({@code org:read}); chỉ HR sửa ({@code org:manage}). */
@Tag(name = "Cơ cấu tổ chức", description = "ORG-02: danh mục chức danh")
@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final PositionService service;
    private final OrgCatalogApiMapper mapper;

    public PositionController(PositionService service, OrgCatalogApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh sách chức danh",
            description = "Xếp theo mã; lọc theo từ khóa (mã hoặc tên), cấp bậc. Không phân trang vì danh mục nhỏ.")
    @PreAuthorize("hasAuthority('org:read')")
    @GetMapping
    public List<PositionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Long jobGradeId,
                                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(RequestActor.companyId(jwt), new Filter(q, jobGradeId, includeInactive)).stream()
                .map(mapper::toResponse).toList();
    }

    @Operation(summary = "Chi tiết chức danh")
    @PreAuthorize("hasAuthority('org:read')")
    @GetMapping("/{id}")
    public PositionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return mapper.toResponse(service.get(RequestActor.companyId(jwt), id));
    }

    @Operation(summary = "Tạo chức danh", description = "Bắt buộc chọn một cấp bậc đang hoạt động.")
    @PreAuthorize("hasAuthority('org:manage')")
    @PostMapping
    public ResponseEntity<PositionResponse> create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                   @Valid @RequestBody CreatePositionRequest request) {
        PositionResponse body = mapper.toResponse(service.create(RequestActor.of(jwt, http), mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Sửa chức danh, đổi cấp bậc")
    @PreAuthorize("hasAuthority('org:manage')")
    @PutMapping("/{id}")
    public PositionResponse update(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                                   @Valid @RequestBody UpdatePositionRequest request) {
        return mapper.toResponse(service.update(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Ngừng hoạt động / hoạt động lại",
            description = "Hoạt động lại khi cấp bậc của chức danh đang hoạt động.")
    @PreAuthorize("hasAuthority('org:manage')")
    @PutMapping("/{id}/status")
    public PositionResponse changeStatus(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                         @PathVariable long id, @Valid @RequestBody PositionStatusRequest request) {
        return mapper.toResponse(service.changeStatus(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Xóa chức danh", description = "Chỉ khi chưa từng có nhân viên giữ chức danh.")
    @PreAuthorize("hasAuthority('org:manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                       @PathVariable long id) {
        service.delete(RequestActor.of(jwt, http), id);
        return ResponseEntity.noContent().build();
    }
}
