package vn.sspack.hrm.org.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Phòng ban trong cây tổ chức (ORG-01). Cây lưu bằng {@code parent_id} và materialized path
 * ({@code /1/4/12/}: id của mọi cấp từ gốc tới chính nó) để lấy cả nhánh bằng một điều kiện {@code LIKE}.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Không giới hạn số cấp (cột {@code path} 1000 ký tự, đủ cho hơn 100 cấp).</li>
 *   <li>Mã viết hoa, chữ số, {@code _} hoặc {@code -}; không trùng trong công ty.</li>
 *   <li>Không chuyển phòng ban vào chính nó hay vào phòng ban con cháu của nó.</li>
 *   <li>Không tạo hay chuyển phòng ban vào dưới một phòng ban đã ngừng hoạt động.</li>
 *   <li>Xóa: chỉ khi không còn phòng ban con và chưa từng có nhân viên.</li>
 *   <li>Ngừng hoạt động: chỉ khi không còn phòng ban con đang hoạt động và không còn nhân viên đang làm việc.</li>
 * </ul>
 */
@Entity
@Table(name = "departments")
public class Department {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,29}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    protected Department() {
    }

    /**
     * Tạo phòng ban dưới {@code parent} (null = cấp cao nhất). {@code path} hoàn tất bằng
     * {@link #completePath()} sau khi CSDL cấp id.
     */
    public static Department create(Long companyId, Department parent, String code, String name,
                                    OffsetDateTime now) {
        if (parent != null && !parent.active) {
            throw DepartmentRuleException.parentInactive();
        }
        Department department = new Department();
        department.companyId = Objects.requireNonNull(companyId);
        department.parentId = parent == null ? null : parent.id;
        department.path = parent == null ? "/" : parent.path;
        department.rename(code, name, now);
        return department;
    }

    /** Gắn id của chính nó vào cuối path; gọi một lần ngay sau khi lưu lần đầu. */
    public void completePath() {
        if (id == null) {
            throw new IllegalStateException("Phòng ban chưa có id");
        }
        if (!path.endsWith("/" + id + "/")) {
            path = path + id + "/";
        }
    }

    public void rename(String newCode, String newName, OffsetDateTime now) {
        String normalized = newCode == null ? "" : newCode.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw DepartmentRuleException.invalidCode();
        }
        code = normalized;
        name = Objects.requireNonNull(newName).trim();
        updatedAt = now;
    }

    /**
     * Chuyển phòng ban sang cấp cha mới (null = cấp cao nhất).
     *
     * @return path cũ, để cập nhật path của cả nhánh con; rỗng nếu cấp cha không đổi
     */
    public Optional<String> moveUnder(Department newParent, OffsetDateTime now) {
        Long newParentId = newParent == null ? null : newParent.id;
        if (Objects.equals(newParentId, parentId)) {
            return Optional.empty();
        }
        if (newParent != null) {
            if (newParent.id.equals(id) || newParent.path.startsWith(path)) {
                throw DepartmentRuleException.moveIntoOwnSubtree();
            }
            if (!newParent.active) {
                throw DepartmentRuleException.parentInactive();
            }
        }
        String oldPath = path;
        parentId = newParentId;
        path = (newParent == null ? "/" : newParent.path) + id + "/";
        updatedAt = now;
        return Optional.of(oldPath);
    }

    public void ensureDeletable(long childCount, long everEmployedCount) {
        if (childCount > 0) {
            throw DepartmentRuleException.hasChildren(childCount);
        }
        if (everEmployedCount > 0) {
            throw DepartmentRuleException.hasEmployees(everEmployedCount);
        }
    }

    public void deactivate(long activeChildCount, long activeEmployeeCount, OffsetDateTime now) {
        if (activeChildCount > 0) {
            throw DepartmentRuleException.hasActiveChildren(activeChildCount);
        }
        if (activeEmployeeCount > 0) {
            throw DepartmentRuleException.hasActiveEmployees(activeEmployeeCount);
        }
        active = false;
        updatedAt = now;
    }

    /** Hoạt động lại; cấp cha (nếu có) phải đang hoạt động. */
    public void activate(Department parent, OffsetDateTime now) {
        if (parent != null && !parent.active) {
            throw DepartmentRuleException.parentInactive();
        }
        active = true;
        updatedAt = now;
    }

    /** Số cấp tính từ gốc: phòng ban cấp cao nhất là 1. */
    public int depth() {
        return (int) path.chars().filter(c -> c == '/').count() - 1;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
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
