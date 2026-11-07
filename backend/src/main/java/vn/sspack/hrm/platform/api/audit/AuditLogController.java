package vn.sspack.hrm.platform.api.audit;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.sspack.hrm.platform.application.audit.AuditLogQueryService;
import vn.sspack.hrm.platform.application.audit.AuditLogQueryService.Criteria;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.shared.api.PageResponse;

/** PLT-04: xem nhật ký thao tác. Chỉ người có {@code audit:read} (mặc định là Admin). */
@Tag(name = "Nhật ký thao tác", description = "PLT-04: ai, lúc nào, bảng nào, giá trị trước/sau")
@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditLogController {

    private final AuditLogQueryService queryService;
    private final AuditApiMapper mapper;

    public AuditLogController(AuditLogQueryService queryService, AuditApiMapper mapper) {
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @Schema(description = "Một dòng nhật ký")
    public record AuditLogResponse(
            long id,
            @Schema(description = "Thời điểm, giờ Việt Nam") OffsetDateTime createdAt,
            @Schema(description = "Người thực hiện; null nếu hệ thống tự làm") Long userId,
            String userEmail,
            @Schema(example = "UPDATE") String action,
            @Schema(description = "Tên bảng", example = "roles") String entityType,
            Long entityId,
            @Schema(description = "Giá trị trước; null khi tạo mới") JsonNode oldValue,
            @Schema(description = "Giá trị sau; null khi xóa") JsonNode newValue,
            String ipAddress) {
    }

    public record ActorResponse(long id, String email) {
    }

    @Operation(summary = "Tra cứu nhật ký",
            description = "Mới nhất trước. Khoảng ngày theo giờ Việt Nam, gồm trọn ngày `to`.")
    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId,
            @Parameter(description = "Tên bảng, vd roles") @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Action action,
            @RequestParam(required = false) Long entityId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        Criteria criteria = new Criteria(from, to, userId, blankToNull(entityType), action, entityId);
        return PageResponse.of(queryService.search(companyId(jwt), criteria, pageable), mapper::toResponse);
    }

    @Operation(summary = "Người từng thao tác", description = "Dùng cho ô lọc người thực hiện.")
    @GetMapping("/actors")
    public List<ActorResponse> actors(@AuthenticationPrincipal Jwt jwt) {
        return queryService.actors(companyId(jwt)).stream().map(mapper::toResponse).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long companyId(Jwt jwt) {
        Object value = jwt.getClaim("company_id");
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
