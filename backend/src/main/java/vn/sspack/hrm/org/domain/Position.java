package vn.sspack.hrm.org.domain;

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
 * Chức danh (ORG-02). Tiêu chí chấp nhận: mỗi chức danh gắn với một khung cấp bậc lương.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Mã 2–30 ký tự in hoa, chữ số, {@code _} hoặc {@code -}; không trùng trong công ty.</li>
 *   <li>Bắt buộc thuộc một cấp bậc của cùng công ty. Khi tạo, đổi cấp bậc hay hoạt động lại, cấp bậc
 *       phải đang hoạt động; chỉ sửa tên thì giữ được cấp bậc đã ngừng.</li>
 *   <li>Ngừng hoạt động lúc nào cũng được; nhân viên đang giữ chức danh không bị ảnh hưởng.</li>
 *   <li>Xóa: chỉ khi chưa từng có nhân viên giữ chức danh.</li>
 * </ul>
 */
@Entity
@Table(name = "positions")
public class Position {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,29}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "job_grade_id", nullable = false)
    private Long jobGradeId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    protected Position() {
    }

    public static Position create(Long companyId, String code, String name, JobGrade grade, OffsetDateTime now) {
        Position position = new Position();
        position.companyId = Objects.requireNonNull(companyId);
        position.update(code, name, grade, now);
        return position;
    }

    public void update(String newCode, String newName, JobGrade grade, OffsetDateTime now) {
        String normalized = newCode == null ? "" : newCode.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw PositionRuleException.invalidCode();
        }
        checkGrade(grade);
        if (!grade.getId().equals(jobGradeId) && !grade.isActive()) {
            throw PositionRuleException.gradeInactive();
        }
        code = normalized;
        name = Objects.requireNonNull(newName).trim();
        jobGradeId = grade.getId();
        updatedAt = now;
    }

    public void ensureDeletable(long everEmployedCount) {
        if (everEmployedCount > 0) {
            throw PositionRuleException.hasEmployees(everEmployedCount);
        }
    }

    public void deactivate(OffsetDateTime now) {
        active = false;
        updatedAt = now;
    }

    /** Hoạt động lại; cấp bậc của chức danh phải đang hoạt động. */
    public void activate(JobGrade grade, OffsetDateTime now) {
        if (!grade.getId().equals(jobGradeId)) {
            throw new IllegalArgumentException("Cấp bậc không phải của chức danh");
        }
        if (!grade.isActive()) {
            throw PositionRuleException.gradeInactive();
        }
        active = true;
        updatedAt = now;
    }

    private void checkGrade(JobGrade grade) {
        if (grade == null || grade.getId() == null || !grade.getCompanyId().equals(companyId)) {
            throw PositionRuleException.gradeRequired();
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

    public Long getJobGradeId() {
        return jobGradeId;
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
