package vn.sspack.hrm.employee.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Hồ sơ nhân viên (EMP-01, phần lõi): thông tin cá nhân, liên hệ và công việc. Giấy tờ tùy thân, ngân hàng,
 * người phụ thuộc, học vấn làm ở đợt sau; nghỉ việc ở EMP-06.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Mã 2–30 ký tự in hoa, chữ số, {@code _} hoặc {@code -}; không trùng trong công ty (kiểm ở service).</li>
 *   <li>Ngày sinh (nếu có) trước ngày vào làm và không ở tương lai.</li>
 *   <li>Phòng ban, chức danh phải đang hoạt động khi gán mới; giữ nguyên giá trị cũ đã ngừng hoạt động vẫn
 *       lưu được để không chặn việc sửa thông tin khác.</li>
 *   <li>Tạo mới chỉ chọn Thử việc hoặc Chính thức; sửa chọn được thêm Nghỉ dài hạn. Nghỉ việc, chấm dứt chỉ
 *       qua EMP-06; hồ sơ đã nghỉ giữ nguyên trạng thái khi sửa.</li>
 * </ul>
 */
@Entity
@Table(name = "employees")
public class Employee {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,29}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{9,15}");
    private static final int NAME_MAX = 150;

    /** Thông tin cá nhân, liên hệ do HR nhập. Chuỗi rỗng coi như không có. */
    public record Profile(String code, String fullName, Gender gender, LocalDate dateOfBirth, String workEmail,
                          String personalEmail, String phone, String address, LocalDate hireDate,
                          EmployeeStatus status) {
    }

    /** Nơi làm việc; cờ {@code active} lấy từ module org tại thời điểm gán. */
    public record Placement(long departmentId, boolean departmentActive, long positionId, boolean positionActive) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    /** Tài khoản đăng nhập gắn với hồ sơ; chưa có giao diện gắn (đợt sau), seed dev gắn sẵn. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String code;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "work_email")
    private String workEmail;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private int version;

    protected Employee() {
    }

    public static Employee create(Long companyId, Profile profile, Placement placement, LocalDate today,
                                  Long actorUserId, OffsetDateTime now) {
        if (profile.status() == null || !EmployeeStatus.ON_CREATE.contains(profile.status())) {
            throw EmployeeRuleException.statusNotAllowed();
        }
        Employee employee = new Employee();
        employee.companyId = Objects.requireNonNull(companyId);
        employee.apply(profile, placement, today);
        employee.status = profile.status();
        employee.createdAt = now;
        employee.createdBy = actorUserId;
        employee.updatedAt = now;
        employee.updatedBy = actorUserId;
        return employee;
    }

    public void update(Profile profile, Placement placement, LocalDate today, Long actorUserId,
                       OffsetDateTime now) {
        EmployeeStatus next = profile.status();
        if (next == null || (status.isLeft() ? next != status : next.isLeft())) {
            throw EmployeeRuleException.statusNotAllowed();
        }
        apply(profile, placement, today);
        status = next;
        updatedAt = now;
        updatedBy = actorUserId;
    }

    /** Trạng thái chọn được khi sửa hồ sơ đang ở {@code current}. */
    public static Set<EmployeeStatus> allowedOnUpdate(EmployeeStatus current) {
        return current.isLeft() ? Set.of(current) : EmployeeStatus.WORKING;
    }

    private void apply(Profile p, Placement placement, LocalDate today) {
        String normalizedCode = normalizeCode(p.code());
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw EmployeeRuleException.invalidCode();
        }
        String name = p.fullName() == null ? "" : p.fullName().trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw EmployeeRuleException.nameRequired();
        }
        LocalDate hire = Objects.requireNonNull(p.hireDate(), "hireDate");
        if (p.dateOfBirth() != null && (!p.dateOfBirth().isBefore(hire) || p.dateOfBirth().isAfter(today))) {
            throw EmployeeRuleException.birthDateInvalid();
        }
        String normalizedPhone = blankToNull(p.phone());
        if (normalizedPhone != null) {
            normalizedPhone = normalizedPhone.replaceAll("[\\s.\\-()]", "");
            if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                throw EmployeeRuleException.phoneInvalid();
            }
        }
        if (!Objects.equals(departmentId, placement.departmentId()) && !placement.departmentActive()) {
            throw EmployeeRuleException.departmentInactive();
        }
        if (!Objects.equals(positionId, placement.positionId()) && !placement.positionActive()) {
            throw EmployeeRuleException.positionInactive();
        }
        code = normalizedCode;
        fullName = name;
        gender = p.gender();
        dateOfBirth = p.dateOfBirth();
        workEmail = email(p.workEmail());
        personalEmail = email(p.personalEmail());
        phone = normalizedPhone;
        address = blankToNull(p.address());
        hireDate = hire;
        departmentId = placement.departmentId();
        positionId = placement.positionId();
    }

    /** Mã sau chuẩn hóa (bỏ khoảng trắng, in hoa), dùng cả khi kiểm trùng trước khi lưu. */
    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** Email sau chuẩn hóa (bỏ khoảng trắng, chữ thường); null nếu rỗng. */
    public static String normalizeEmail(String value) {
        return email(value);
    }

    private static String email(String value) {
        String v = blankToNull(value);
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public String getFullName() {
        return fullName;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
