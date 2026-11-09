package vn.sspack.hrm.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.function.Executable;

import vn.sspack.hrm.employee.domain.Employee.Placement;
import vn.sspack.hrm.employee.domain.Employee.Profile;
import vn.sspack.hrm.shared.error.ApiException;

/** EMP-01: quy tắc hồ sơ nhân viên (phần lõi). */
class EmployeeTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 9, 8, 0, 0, 0, ZoneOffset.ofHours(7));
    static final OffsetDateTime T1 = T0.plusHours(1);
    static final Placement ACTIVE_PLACEMENT = new Placement(10, true, 20, true);

    static Profile profile(String code, EmployeeStatus status) {
        return new Profile(code, "  Phạm   Văn Đức ", Gender.MALE, LocalDate.of(1991, 5, 15),
                " VanDuc.Pham@SSPack.vn ", "", " 0903 110-011 ", "  ", LocalDate.of(2021, 8, 15), status);
    }

    static Employee created(EmployeeStatus status) {
        return Employee.create(1L, profile("sp0011", status), ACTIVE_PLACEMENT, TODAY, 99L, T0);
    }

    static String codeOf(Executable action) {
        ApiException e = catchThrowableOfType(ApiException.class, () -> action.execute());
        assertThat(e).as("phải ném ApiException").isNotNull();
        return e.code();
    }

    @Test
    @DisplayName("Tạo: chuẩn hóa mã, tên, email, điện thoại; chuỗi rỗng thành null; ghi người tạo")
    void createNormalizes() {
        Employee e = created(EmployeeStatus.PROBATION);

        assertThat(e.getCode()).isEqualTo("SP0011");
        assertThat(e.getFullName()).isEqualTo("Phạm Văn Đức");
        assertThat(e.getWorkEmail()).isEqualTo("vanduc.pham@sspack.vn");
        assertThat(e.getPersonalEmail()).isNull();
        assertThat(e.getPhone()).isEqualTo("0903110011");
        assertThat(e.getAddress()).isNull();
        assertThat(e.getDepartmentId()).isEqualTo(10L);
        assertThat(e.getPositionId()).isEqualTo(20L);
        assertThat(e.getStatus()).isEqualTo(EmployeeStatus.PROBATION);
        assertThat(e.getUpdatedAt()).isEqualTo(T0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A", "SP 01", "SP#1", "-SP01", "SP0123456789012345678901234567890"})
    @DisplayName("Mã sai định dạng: EMPLOYEE_CODE_INVALID")
    void invalidCode(String code) {
        assertThat(codeOf(() -> Employee.create(1L, profile(code, EmployeeStatus.ACTIVE), ACTIVE_PLACEMENT, TODAY,
                1L, T0))).isEqualTo("EMPLOYEE_CODE_INVALID");
    }

    @Test
    @DisplayName("Họ tên rỗng hoặc quá 150 ký tự: EMPLOYEE_NAME_REQUIRED")
    void nameRequired() {
        for (String name : new String[] {"   ", "A".repeat(151)}) {
            Profile p = new Profile("SP1", name, null, null, null, null, null, null, TODAY, EmployeeStatus.ACTIVE);
            assertThat(codeOf(() -> Employee.create(1L, p, ACTIVE_PLACEMENT, TODAY, 1L, T0)))
                    .isEqualTo("EMPLOYEE_NAME_REQUIRED");
        }
    }

    @Nested
    @DisplayName("Ngày sinh")
    class BirthDate {

        Profile withBirth(LocalDate birth, LocalDate hire) {
            return new Profile("SP1", "Lê Thu Hà", Gender.FEMALE, birth, null, null, null, null, hire,
                    EmployeeStatus.ACTIVE);
        }

        @Test
        @DisplayName("Không nhập ngày sinh vẫn lưu được")
        void optional() {
            assertThatCode(() -> Employee.create(1L, withBirth(null, TODAY), ACTIVE_PLACEMENT, TODAY, 1L, T0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ngày sinh trùng hoặc sau ngày vào làm, hoặc ở tương lai: EMPLOYEE_BIRTH_DATE_INVALID")
        void invalid() {
            LocalDate hire = LocalDate.of(2020, 1, 1);
            assertThat(codeOf(() -> Employee.create(1L, withBirth(hire, hire), ACTIVE_PLACEMENT, TODAY, 1L, T0)))
                    .isEqualTo("EMPLOYEE_BIRTH_DATE_INVALID");
            assertThat(codeOf(() -> Employee.create(1L, withBirth(hire.plusDays(1), hire), ACTIVE_PLACEMENT, TODAY,
                    1L, T0))).isEqualTo("EMPLOYEE_BIRTH_DATE_INVALID");
            // Vào làm ở tương lai (đã ký nhận việc) nhưng ngày sinh cũng ở tương lai
            LocalDate futureHire = TODAY.plusMonths(2);
            assertThat(codeOf(() -> Employee.create(1L, withBirth(TODAY.plusDays(1), futureHire), ACTIVE_PLACEMENT,
                    TODAY, 1L, T0))).isEqualTo("EMPLOYEE_BIRTH_DATE_INVALID");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "09031100110011001", "090311001a", "++84903110011"})
    @DisplayName("Điện thoại không phải 9–15 chữ số: EMPLOYEE_PHONE_INVALID")
    void invalidPhone(String phone) {
        Profile p = new Profile("SP1", "Lê Thu Hà", null, null, null, null, phone, null, TODAY, EmployeeStatus.ACTIVE);
        assertThat(codeOf(() -> Employee.create(1L, p, ACTIVE_PLACEMENT, TODAY, 1L, T0)))
                .isEqualTo("EMPLOYEE_PHONE_INVALID");
    }

    @Test
    @DisplayName("Điện thoại quốc tế có dấu + hợp lệ")
    void internationalPhone() {
        Profile p = new Profile("SP1", "Lê Thu Hà", null, null, null, null, "+84 903 110 011", null, TODAY,
                EmployeeStatus.ACTIVE);
        assertThat(Employee.create(1L, p, ACTIVE_PLACEMENT, TODAY, 1L, T0).getPhone()).isEqualTo("+84903110011");
    }

    @Nested
    @DisplayName("Trạng thái")
    class Status {

        @ParameterizedTest
        @EnumSource(value = EmployeeStatus.class, names = {"ON_LEAVE", "RESIGNED", "TERMINATED"})
        @DisplayName("Tạo mới chỉ được Thử việc hoặc Chính thức")
        void createOnlyProbationOrActive(EmployeeStatus status) {
            assertThat(codeOf(() -> created(status))).isEqualTo("EMPLOYEE_STATUS_NOT_ALLOWED");
        }

        @Test
        @DisplayName("Sửa: chuyển giữa Thử việc, Chính thức, Nghỉ dài hạn")
        void updateWorkingStatuses() {
            Employee e = created(EmployeeStatus.PROBATION);
            e.update(profile("SP0011", EmployeeStatus.ACTIVE), ACTIVE_PLACEMENT, TODAY, 7L, T1);
            assertThat(e.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
            e.update(profile("SP0011", EmployeeStatus.ON_LEAVE), ACTIVE_PLACEMENT, TODAY, 7L, T1);
            assertThat(e.getStatus()).isEqualTo(EmployeeStatus.ON_LEAVE);
            assertThat(e.getUpdatedAt()).isEqualTo(T1);
        }

        @Test
        @DisplayName("Sửa hồ sơ không đặt được Nghỉ việc, Chấm dứt (EMP-06)")
        void cannotSetLeftStatus() {
            Employee e = created(EmployeeStatus.ACTIVE);
            assertThat(codeOf(() -> e.update(profile("SP0011", EmployeeStatus.RESIGNED), ACTIVE_PLACEMENT, TODAY,
                    7L, T1))).isEqualTo("EMPLOYEE_STATUS_NOT_ALLOWED");
            assertThat(codeOf(() -> e.update(profile("SP0011", null), ACTIVE_PLACEMENT, TODAY, 7L, T1)))
                    .isEqualTo("EMPLOYEE_STATUS_NOT_ALLOWED");
        }

        @Test
        @DisplayName("Danh sách trạng thái chọn được khi sửa")
        void allowedOnUpdate() {
            assertThat(Employee.allowedOnUpdate(EmployeeStatus.ACTIVE)).isEqualTo(EmployeeStatus.WORKING);
            assertThat(Employee.allowedOnUpdate(EmployeeStatus.RESIGNED)).containsExactly(EmployeeStatus.RESIGNED);
        }
    }

    @Nested
    @DisplayName("Phòng ban, chức danh")
    class PlacementRules {

        @Test
        @DisplayName("Gán mới vào phòng ban hoặc chức danh đã ngừng hoạt động: 409")
        void inactiveOnCreate() {
            assertThat(codeOf(() -> Employee.create(1L, profile("SP1", EmployeeStatus.ACTIVE),
                    new Placement(10, false, 20, true), TODAY, 1L, T0))).isEqualTo("EMPLOYEE_DEPARTMENT_INACTIVE");
            assertThat(codeOf(() -> Employee.create(1L, profile("SP1", EmployeeStatus.ACTIVE),
                    new Placement(10, true, 20, false), TODAY, 1L, T0))).isEqualTo("EMPLOYEE_POSITION_INACTIVE");
        }

        @Test
        @DisplayName("Giữ nguyên phòng ban, chức danh đã ngừng hoạt động vẫn sửa được thông tin khác")
        void keepInactive() {
            Employee e = created(EmployeeStatus.ACTIVE);
            Profile renamed = new Profile("SP0011", "Phạm Văn Đức Anh", Gender.MALE, null, null, null, null, null,
                    LocalDate.of(2021, 8, 15), EmployeeStatus.ACTIVE);

            e.update(renamed, new Placement(10, false, 20, false), TODAY, 7L, T1);

            assertThat(e.getFullName()).isEqualTo("Phạm Văn Đức Anh");
        }

        @Test
        @DisplayName("Chuyển sang phòng ban khác đã ngừng hoạt động: 409")
        void moveToInactive() {
            Employee e = created(EmployeeStatus.ACTIVE);
            assertThat(codeOf(() -> e.update(profile("SP0011", EmployeeStatus.ACTIVE),
                    new Placement(11, false, 20, true), TODAY, 7L, T1))).isEqualTo("EMPLOYEE_DEPARTMENT_INACTIVE");
            assertThat(e.getDepartmentId()).isEqualTo(10L);
        }
    }

    @Test
    @DisplayName("Chuẩn hóa dùng chung khi kiểm trùng trước khi lưu")
    void normalizers() {
        assertThat(Employee.normalizeCode(" sp0001 ")).isEqualTo("SP0001");
        assertThat(Employee.normalizeEmail(" A@B.VN ")).isEqualTo("a@b.vn");
        assertThat(Employee.normalizeEmail("  ")).isNull();
    }
}
