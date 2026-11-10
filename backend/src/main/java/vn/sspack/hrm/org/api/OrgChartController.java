package vn.sspack.hrm.org.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.org.application.OrgChartService;
import vn.sspack.hrm.org.application.OrgChartService.ChartDepartment;
import vn.sspack.hrm.org.application.OrgChartService.Member;
import vn.sspack.hrm.shared.api.RequestActor;

/** ORG-03: sơ đồ tổ chức cho mọi người dùng có {@code org:read}. */
@Tag(name = "Cơ cấu tổ chức", description = "ORG-03: sơ đồ tổ chức")
@RestController
@RequestMapping("/api/v1/org-chart")
public class OrgChartController {

    @Schema(description = "Người trên sơ đồ: chỉ thông tin công việc")
    public record MemberResponse(
            Long id,
            @Schema(example = "SP0010") String code,
            @Schema(example = "Trần Minh Anh") String fullName,
            @Schema(example = "Quản đốc xưởng") String positionName,
            String workEmail,
            @Schema(description = "Ảnh đại diện; null cho đến khi có ảnh hồ sơ (EMP-07)") String avatarUrl,
            @Schema(description = "Người xem mở được hồ sơ đầy đủ") boolean profileVisible,
            @Schema(description = "Thẻ của chính người xem") boolean self) {
    }

    @Schema(description = "Phòng ban đang hoạt động, xếp theo thứ tự cây (cha trước con)")
    public record ChartDepartmentResponse(Long id, Long parentId, String code, String name, int depth,
                                          List<MemberResponse> members) {
    }

    private final OrgChartService service;

    public OrgChartController(OrgChartService service) {
        this.service = service;
    }

    @Operation(summary = "Sơ đồ tổ chức",
            description = "Cây phòng ban đang hoạt động kèm nhân viên đang làm việc. Cả công ty, chỉ thông tin "
                    + "công việc; trong phòng ban, cấp bậc cao đứng trước.")
    @PreAuthorize("hasAuthority('org:read')")
    @GetMapping
    public List<ChartDepartmentResponse> chart(@AuthenticationPrincipal Jwt jwt) {
        return service.chart(RequestActor.companyId(jwt), Long.parseLong(jwt.getSubject())).stream()
                .map(OrgChartController::toResponse)
                .toList();
    }

    private static ChartDepartmentResponse toResponse(ChartDepartment d) {
        return new ChartDepartmentResponse(d.id(), d.parentId(), d.code(), d.name(), d.depth(),
                d.members().stream().map(OrgChartController::toResponse).toList());
    }

    private static MemberResponse toResponse(Member m) {
        return new MemberResponse(m.id(), m.code(), m.fullName(), m.positionName(), m.workEmail(), m.avatarUrl(),
                m.profileVisible(), m.self());
    }
}
