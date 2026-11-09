package vn.sspack.hrm.org.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Cấp bậc kèm khung lương (ORG-02). Mỗi chức danh gắn với đúng một cấp bậc.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Mã 2–20 ký tự in hoa, chữ số, {@code _} hoặc {@code -}; không trùng trong công ty.</li>
 *   <li>Khung lương bắt buộc: tối thiểu và tối đa là số đồng nguyên, lớn hơn 0, tối thiểu ≤ tối đa,
 *       không quá 15 chữ số (cột {@code NUMERIC(15,0)}).</li>
 *   <li>Ngừng hoạt động: chỉ khi không còn chức danh đang hoạt động thuộc cấp bậc.</li>
 *   <li>Xóa: chỉ khi chưa có chức danh nào thuộc cấp bậc (kể cả chức danh đã ngừng hoạt động).</li>
 * </ul>
 */
@Entity
@Table(name = "job_grades")
public class JobGrade {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,19}");
    /** Giới hạn của {@code NUMERIC(15,0)}: tối đa 15 chữ số. */
    private static final BigDecimal SALARY_LIMIT = new BigDecimal("1000000000000000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_salary", nullable = false, precision = 15, scale = 0)
    private BigDecimal minSalary;

    @Column(name = "max_salary", nullable = false, precision = 15, scale = 0)
    private BigDecimal maxSalary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    protected JobGrade() {
    }

    public static JobGrade create(Long companyId, String code, String name, BigDecimal minSalary,
                                  BigDecimal maxSalary, OffsetDateTime now) {
        JobGrade grade = new JobGrade();
        grade.companyId = Objects.requireNonNull(companyId);
        grade.update(code, name, minSalary, maxSalary, now);
        return grade;
    }

    public void update(String newCode, String newName, BigDecimal newMin, BigDecimal newMax, OffsetDateTime now) {
        String normalized = newCode == null ? "" : newCode.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw JobGradeRuleException.invalidCode();
        }
        BigDecimal min = wholeDong(newMin);
        BigDecimal max = wholeDong(newMax);
        if (min.compareTo(max) > 0) {
            throw JobGradeRuleException.rangeInverted();
        }
        code = normalized;
        name = Objects.requireNonNull(newName).trim();
        minSalary = min;
        maxSalary = max;
        updatedAt = now;
    }

    public void ensureDeletable(long positionCount) {
        if (positionCount > 0) {
            throw JobGradeRuleException.hasPositions(positionCount);
        }
    }

    public void deactivate(long activePositionCount, OffsetDateTime now) {
        if (activePositionCount > 0) {
            throw JobGradeRuleException.hasActivePositions(activePositionCount);
        }
        active = false;
        updatedAt = now;
    }

    public void activate(OffsetDateTime now) {
        active = true;
        updatedAt = now;
    }

    /** Lương tính bằng đồng: lớn hơn 0, không có phần lẻ, vừa cột NUMERIC(15,0). */
    private static BigDecimal wholeDong(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(SALARY_LIMIT) >= 0) {
            throw JobGradeRuleException.salaryInvalid();
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw JobGradeRuleException.salaryInvalid();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMinSalary() {
        return minSalary;
    }

    public BigDecimal getMaxSalary() {
        return maxSalary;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
