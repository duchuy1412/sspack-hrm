package vn.sspack.hrm.org.api;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO của API cây phòng ban (ORG-01). */
public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    static final String CODE_REGEX = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}";
    static final String CODE_MESSAGE = "Mã gồm 2–30 ký tự: chữ, số, dấu gạch dưới hoặc gạch ngang";

    @Schema(description = "Phòng ban; danh sách trả theo thứ tự cây, cha trước con")
    public record DepartmentResponse(
            Long id,
            @Schema(description = "null = cấp cao nhất") Long parentId,
            @Schema(example = "XUONG-IN") String code,
            String name,
            @Schema(description = "Id các cấp từ gốc tới chính nó", example = "/1/4/12/") String path,
            @Schema(description = "Cấp, phòng ban cấp cao nhất là 1") int depth,
            boolean active,
            int childCount,
            @Schema(description = "Số nhân viên từng thuộc phòng ban (mọi trạng thái)") long employeeCount,
            @Schema(description = "Số nhân viên đang làm việc") long workingEmployeeCount,
            @Schema(description = "Gửi lại khi sửa để phát hiện sửa trùng") int version,
            OffsetDateTime updatedAt,
            @Schema(description = "Trưởng đơn vị; null = chưa chỉ định") PersonResponse head,
            @Schema(description = "Người duyệt cấp 1 của nhân viên thường trong phòng ban (trưởng, hoặc trưởng cấp trên "
                    + "khi chưa có); null = không có ai") PersonResponse effectiveApprover) {
    }

    @Schema(description = "Nhân viên được nhắc tới trên cây phòng ban")
    public record PersonResponse(
            Long employeeId,
            String code,
            String fullName,
            @Schema(description = "Chỉ có với trưởng đơn vị: NO_ACCOUNT (chưa có tài khoản), "
                    + "NO_MANAGER_SCOPE (vai trò không có quyền quản lý)") List<String> warnings) {
    }

    @Schema(description = "Chỉ định hoặc bỏ trưởng đơn vị")
    public record HeadRequest(
            @Schema(description = "Nhân viên làm trưởng; null = bỏ trưởng") Long employeeId,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    @Schema(description = "Tạo phòng ban")
    public record CreateDepartmentRequest(
            @NotBlank(message = "Vui lòng nhập mã phòng ban")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên phòng ban")
            @Size(max = 255, message = "Tên phòng ban tối đa 255 ký tự") String name,
            @Schema(description = "Phòng ban cấp trên; bỏ trống = cấp cao nhất") Long parentId) {
    }

    @Schema(description = "Sửa mã, tên phòng ban")
    public record UpdateDepartmentRequest(
            @NotBlank(message = "Vui lòng nhập mã phòng ban")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập tên phòng ban")
            @Size(max = 255, message = "Tên phòng ban tối đa 255 ký tự") String name,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    @Schema(description = "Đổi cấp cha (kéo thả)")
    public record MoveDepartmentRequest(
            @Schema(description = "Phòng ban cấp trên mới; null = đưa lên cấp cao nhất") Long parentId,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }

    @Schema(description = "Ngừng hoạt động hoặc hoạt động lại")
    public record DepartmentStatusRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") Boolean active,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }
}
