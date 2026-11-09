package vn.sspack.hrm.employee.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import vn.sspack.hrm.employee.domain.EmployeeStatus;
import vn.sspack.hrm.employee.domain.Gender;

/** DTO của API hồ sơ nhân viên (EMP-01). */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    static final String CODE_REGEX = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}";
    static final String CODE_MESSAGE = "Mã gồm 2–30 ký tự: chữ, số, dấu gạch dưới hoặc gạch ngang";
    static final String PHONE_REGEX = "^$|^\\+?[0-9 .()-]{9,20}$";
    static final String PHONE_MESSAGE = "Số điện thoại gồm 9–15 chữ số, có thể bắt đầu bằng dấu +";

    @Schema(description = "Một dòng trong danh sách nhân viên")
    public record EmployeeSummaryResponse(
            Long id,
            @Schema(example = "SP0011") String code,
            @Schema(example = "Phạm Văn Đức") String fullName,
            @Schema(example = "vanduc.pham@sspack.vn") String workEmail,
            Long departmentId,
            String departmentName,
            Long positionId,
            String positionName,
            LocalDate hireDate,
            EmployeeStatus status) {
    }

    @Schema(description = "Hồ sơ nhân viên. Trường cá nhân là null khi personalVisible = false.")
    public record EmployeeResponse(
            Long id,
            String code,
            String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            String workEmail,
            String personalEmail,
            String phone,
            String address,
            Long departmentId,
            String departmentName,
            boolean departmentActive,
            Long positionId,
            String positionName,
            boolean positionActive,
            LocalDate hireDate,
            EmployeeStatus status,
            @Schema(description = "Người xem được thấy ngày sinh, email cá nhân, điện thoại, địa chỉ")
            boolean personalVisible,
            @Schema(description = "Hồ sơ đã gắn tài khoản đăng nhập") boolean accountLinked,
            @Schema(description = "Người xem sửa được hồ sơ") boolean editable,
            @Schema(description = "Trạng thái chọn được khi sửa") Set<EmployeeStatus> allowedStatuses,
            @Schema(description = "Gửi lại khi sửa để phát hiện sửa trùng") int version,
            OffsetDateTime updatedAt) {
    }

    @Schema(description = "Mã nhân viên gợi ý cho hồ sơ mới")
    public record NextCodeResponse(@Schema(example = "SP0026") String code) {
    }

    @Schema(description = "Tạo hồ sơ nhân viên")
    public record CreateEmployeeRequest(
            @NotBlank(message = "Vui lòng nhập mã nhân viên")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập họ và tên")
            @Size(max = 150, message = "Họ và tên tối đa 150 ký tự") String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            @Email(message = "Email công việc không hợp lệ")
            @Size(max = 255, message = "Email tối đa 255 ký tự") String workEmail,
            @Email(message = "Email cá nhân không hợp lệ")
            @Size(max = 255, message = "Email tối đa 255 ký tự") String personalEmail,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String address,
            @NotNull(message = "Vui lòng chọn phòng ban") Long departmentId,
            @NotNull(message = "Vui lòng chọn chức danh") Long positionId,
            @NotNull(message = "Vui lòng nhập ngày vào làm") LocalDate hireDate,
            @NotNull(message = "Vui lòng chọn trạng thái") EmployeeStatus status) {
    }

    @Schema(description = "Sửa hồ sơ nhân viên")
    public record UpdateEmployeeRequest(
            @NotBlank(message = "Vui lòng nhập mã nhân viên")
            @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank(message = "Vui lòng nhập họ và tên")
            @Size(max = 150, message = "Họ và tên tối đa 150 ký tự") String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            @Email(message = "Email công việc không hợp lệ")
            @Size(max = 255, message = "Email tối đa 255 ký tự") String workEmail,
            @Email(message = "Email cá nhân không hợp lệ")
            @Size(max = 255, message = "Email tối đa 255 ký tự") String personalEmail,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String address,
            @NotNull(message = "Vui lòng chọn phòng ban") Long departmentId,
            @NotNull(message = "Vui lòng chọn chức danh") Long positionId,
            @NotNull(message = "Vui lòng nhập ngày vào làm") LocalDate hireDate,
            @NotNull(message = "Vui lòng chọn trạng thái") EmployeeStatus status,
            @NotNull(message = "Thiếu phiên bản dữ liệu") Integer version) {
    }
}
