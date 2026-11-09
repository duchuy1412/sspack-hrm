package vn.sspack.hrm.org.api;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO của API chức danh (ORG-02). */
public final class PositionDtos {

    private PositionDtos() {
    }

    static final String CODE_REGEX = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}";
    static final String CODE_MESSAGE = "Mã gồm 2–30 ký tự: chữ, số, dấu gạch dưới hoặc gạch ngang";

    @Schema(description = "Chức danh kèm cấp bậc; không có khung lương (xem ở API cấp bậc, cần quyền org:manage)")
    public record PositionResponse(
            Long id,
            @Schema(example = "KS-QC") String code,
            @Schema(example = "Kỹ sư chất lượng") String name,
            Long jobGradeId,
            @Schema(example = "G3") String jobGradeCode,
            String jobGradeName,
            @Schema(description = "Cấp bậc còn hoạt động hay không") boolean jobGradeActive,
            boolean active,
            @Schema(description = "Số nhân viên từng giữ chức danh (mọi trạng thái)") long employeeCount,
            @Schema(description = "Số nhân viên đang làm việc giữ chức danh") long workingEmployeeCount,
            @Schema(description = "Gửi lại khi sửa để phát hiện sửa trùng") int version,
            OffsetDateTime updatedAt) {
    }

    @Schema(description = "Tạo chức danh")
    public record CreatePositionRequest(
            @NotBlank(message = "Vui lòng nhập mã chức danh")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên chức danh")
            @Size(max = 150, message = "Tên chức danh tối đa 150 ký tự") String name,
            @NotNull(message = "Vui lòng chọn cấp bậc") Long jobGradeId) {
    }

    @Schema(description = "Sửa chức danh, đổi cấp bậc")
    public record UpdatePositionRequest(
            @NotBlank(message = "Vui lòng nhập mã chức danh")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên chức danh")
            @Size(max = 150, message = "Tên chức danh tối đa 150 ký tự") String name,
            @NotNull(message = "Vui lòng chọn cấp bậc") Long jobGradeId,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    @Schema(description = "Ngừng hoạt động hoặc hoạt động lại")
    public record PositionStatusRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") Boolean active,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }
}
