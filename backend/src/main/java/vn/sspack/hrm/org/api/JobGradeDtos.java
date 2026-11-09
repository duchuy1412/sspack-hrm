package vn.sspack.hrm.org.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** DTO của API cấp bậc và khung lương (ORG-02). Tiền tính bằng đồng, số nguyên. */
public final class JobGradeDtos {

    private JobGradeDtos() {
    }

    static final String CODE_REGEX = "[A-Za-z0-9][A-Za-z0-9_-]{1,19}";
    static final String CODE_MESSAGE = "Mã gồm 2–20 ký tự: chữ, số, dấu gạch dưới hoặc gạch ngang";
    static final String SALARY_MESSAGE = "Mức lương là số đồng nguyên, tối đa 15 chữ số";

    @Schema(description = "Cấp bậc kèm khung lương; danh sách xếp theo khung lương tăng dần")
    public record JobGradeResponse(
            Long id,
            @Schema(example = "G3") String code,
            @Schema(example = "Chuyên viên, kỹ thuật viên") String name,
            @Schema(description = "Lương tối thiểu (đồng)", example = "12000000") BigDecimal minSalary,
            @Schema(description = "Lương tối đa (đồng)", example = "20000000") BigDecimal maxSalary,
            boolean active,
            @Schema(description = "Số chức danh thuộc cấp bậc, kể cả đã ngừng hoạt động") long positionCount,
            @Schema(description = "Số chức danh đang hoạt động") long activePositionCount,
            @Schema(description = "Gửi lại khi sửa để phát hiện sửa trùng") int version,
            OffsetDateTime updatedAt) {
    }

    @Schema(description = "Tạo cấp bậc")
    public record CreateJobGradeRequest(
            @NotBlank(message = "Vui lòng nhập mã cấp bậc")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên cấp bậc")
            @Size(max = 100, message = "Tên cấp bậc tối đa 100 ký tự") String name,
            @NotNull(message = "Vui lòng nhập lương tối thiểu")
            @Positive(message = "Lương tối thiểu phải lớn hơn 0")
            @Digits(integer = 15, fraction = 0, message = SALARY_MESSAGE) BigDecimal minSalary,
            @NotNull(message = "Vui lòng nhập lương tối đa")
            @Positive(message = "Lương tối đa phải lớn hơn 0")
            @Digits(integer = 15, fraction = 0, message = SALARY_MESSAGE) BigDecimal maxSalary) {
    }

    @Schema(description = "Sửa cấp bậc và khung lương")
    public record UpdateJobGradeRequest(
            @NotBlank(message = "Vui lòng nhập mã cấp bậc")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên cấp bậc")
            @Size(max = 100, message = "Tên cấp bậc tối đa 100 ký tự") String name,
            @NotNull(message = "Vui lòng nhập lương tối thiểu")
            @Positive(message = "Lương tối thiểu phải lớn hơn 0")
            @Digits(integer = 15, fraction = 0, message = SALARY_MESSAGE) BigDecimal minSalary,
            @NotNull(message = "Vui lòng nhập lương tối đa")
            @Positive(message = "Lương tối đa phải lớn hơn 0")
            @Digits(integer = 15, fraction = 0, message = SALARY_MESSAGE) BigDecimal maxSalary,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    @Schema(description = "Ngừng hoạt động hoặc hoạt động lại")
    public record JobGradeStatusRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") Boolean active,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }
}
