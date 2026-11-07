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

import vn.sspack.hrm.org.api.DepartmentDtos.CreateDepartmentRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.DepartmentResponse;
import vn.sspack.hrm.org.api.DepartmentDtos.DepartmentStatusRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.MoveDepartmentRequest;
import vn.sspack.hrm.org.api.DepartmentDtos.UpdateDepartmentRequest;
import vn.sspack.hrm.org.application.DepartmentService;
import vn.sspack.hrm.shared.api.RequestActor;

/** ORG-01: cây phòng ban. Mọi người dùng xem được ({@code org:read}); chỉ HR sửa ({@code org:manage}). */
@Tag(name = "Cơ cấu tổ chức", description = "ORG-01: cây phòng ban nhiều cấp")
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService service;
    private final DepartmentApiMapper mapper;

    public DepartmentController(DepartmentService service, DepartmentApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh sách phòng ban",
            description = "Toàn bộ cây theo thứ tự cha trước con; không phân trang vì frontend dựng cây từ đây.")
    @PreAuthorize("hasAuthority('org:read')")
    @GetMapping
    public List<DepartmentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(RequestActor.companyId(jwt), includeInactive).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Chi tiết phòng ban")
    @PreAuthorize("hasAuthority('org:read')")
    @GetMapping("/{id}")
    public DepartmentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return mapper.toResponse(service.get(RequestActor.companyId(jwt), id));
    }

    @Operation(summary = "Tạo phòng ban", description = "Mã được chuyển về chữ in hoa.")
    @PreAuthorize("hasAuthority('org:manage')")
    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                     @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse body = mapper.toResponse(service.create(RequestActor.of(jwt, http), mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Sửa mã, tên phòng ban")
    @PreAuthorize("hasAuthority('org:manage')")
    @PutMapping("/{id}")
    public DepartmentResponse update(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                                     @Valid @RequestBody UpdateDepartmentRequest request) {
        return mapper.toResponse(service.update(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Đổi cấp cha",
            description = "Kéo thả trên cây. Không chuyển được vào chính nó hay phòng ban cấp dưới của nó.")
    @PreAuthorize("hasAuthority('org:manage')")
    @PutMapping("/{id}/parent")
    public DepartmentResponse move(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                                   @Valid @RequestBody MoveDepartmentRequest request) {
        return mapper.toResponse(service.move(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Ngừng hoạt động / hoạt động lại",
            description = "Ngừng hoạt động khi không còn nhân viên đang làm việc và phòng ban con đang hoạt động.")
    @PreAuthorize("hasAuthority('org:manage')")
    @PutMapping("/{id}/status")
    public DepartmentResponse changeStatus(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                           @PathVariable long id,
                                           @Valid @RequestBody DepartmentStatusRequest request) {
        return mapper.toResponse(service.changeStatus(RequestActor.of(jwt, http), id, mapper.toCommand(request)));
    }

    @Operation(summary = "Xóa phòng ban",
            description = "Chỉ khi không còn phòng ban cấp dưới và chưa từng có nhân viên.")
    @PreAuthorize("hasAuthority('org:manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                       @PathVariable long id) {
        service.delete(RequestActor.of(jwt, http), id);
        return ResponseEntity.noContent().build();
    }
}
