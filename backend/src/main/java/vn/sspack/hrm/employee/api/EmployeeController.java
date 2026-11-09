package vn.sspack.hrm.employee.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.employee.api.EmployeeDtos.CreateEmployeeRequest;
import vn.sspack.hrm.employee.api.EmployeeDtos.EmployeeResponse;
import vn.sspack.hrm.employee.api.EmployeeDtos.EmployeeSummaryResponse;
import vn.sspack.hrm.employee.api.EmployeeDtos.NextCodeResponse;
import vn.sspack.hrm.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import vn.sspack.hrm.employee.application.EmployeeService;
import vn.sspack.hrm.employee.application.EmployeeService.Filter;
import vn.sspack.hrm.employee.application.EmployeeService.Viewer;
import vn.sspack.hrm.employee.domain.EmployeeStatus;
import vn.sspack.hrm.shared.api.PageResponse;
import vn.sspack.hrm.shared.api.RequestActor;

/**
 * EMP-01: hồ sơ nhân viên. Xem cần {@code employee:read} và chỉ thấy hồ sơ trong phạm vi dữ liệu của mình;
 * tạo, sửa cần {@code employee:manage}. Không có xóa: nhân viên nghỉ được xử lý ở EMP-06.
 */
@Tag(name = "Nhân viên", description = "EMP-01: hồ sơ nhân viên")
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    static final String MANAGE = "employee:manage";

    private final EmployeeService service;
    private final EmployeeApiMapper mapper;

    public EmployeeController(EmployeeService service, EmployeeApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Danh sách nhân viên",
            description = "Trong phạm vi dữ liệu của người xem, xếp theo mã. Tìm theo tên (không dấu), mã, email; "
                    + "lọc phòng ban (gồm phòng ban cấp dưới) và một hoặc nhiều trạng thái.")
    @PreAuthorize("hasAuthority('employee:read')")
    @GetMapping
    public PageResponse<EmployeeSummaryResponse> list(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) Long departmentId,
                                                      @RequestParam(required = false) Set<EmployeeStatus> status,
                                                      @Parameter(hidden = true) @PageableDefault(size = 20)
                                                      Pageable pageable) {
        // Thứ tự cố định theo mã: không nhận sort tùy ý từ client
        Pageable byCode = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("code"));
        return PageResponse.of(service.list(viewer(jwt, auth), new Filter(q, departmentId, status), byCode),
                mapper::toResponse);
    }

    @Operation(summary = "Mã nhân viên gợi ý cho hồ sơ mới")
    @PreAuthorize("hasAuthority('employee:manage')")
    @GetMapping("/next-code")
    public NextCodeResponse nextCode(@AuthenticationPrincipal Jwt jwt) {
        return new NextCodeResponse(service.nextCode(RequestActor.companyId(jwt)));
    }

    @Operation(summary = "Hồ sơ nhân viên",
            description = "404 nếu hồ sơ nằm ngoài phạm vi dữ liệu. Thông tin cá nhân chỉ trả cho người có quyền "
                    + "sửa hồ sơ hoặc chính chủ.")
    @PreAuthorize("hasAuthority('employee:read')")
    @GetMapping("/{id}")
    public EmployeeResponse get(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable long id) {
        return mapper.toResponse(service.get(viewer(jwt, auth), id));
    }

    @Operation(summary = "Tạo hồ sơ nhân viên", description = "Trạng thái khi tạo: Thử việc hoặc Chính thức.")
    @PreAuthorize("hasAuthority('employee:manage')")
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                   @Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse body = mapper.toResponse(
                service.create(RequestActor.of(jwt, http), true, mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Sửa hồ sơ nhân viên")
    @PreAuthorize("hasAuthority('employee:manage')")
    @PutMapping("/{id}")
    public EmployeeResponse update(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable long id,
                                   @Valid @RequestBody UpdateEmployeeRequest request) {
        return mapper.toResponse(service.update(RequestActor.of(jwt, http), true, id, mapper.toCommand(request)));
    }

    private static Viewer viewer(Jwt jwt, Authentication auth) {
        boolean canManage = auth.getAuthorities().stream().anyMatch(a -> MANAGE.equals(a.getAuthority()));
        return new Viewer(RequestActor.companyId(jwt), Long.parseLong(jwt.getSubject()), canManage);
    }
}
