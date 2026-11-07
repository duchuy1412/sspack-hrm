package vn.sspack.hrm.platform.domain.auth;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

/**
 * Vai trò: tập quyền theo chức năng cộng với phạm vi dữ liệu (PLT-03).
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Mã viết hoa ({@code A-Z}, {@code 0-9}, {@code _}), không đổi sau khi tạo.</li>
 *   <li>Vai trò phải có ít nhất một quyền.</li>
 *   <li>Vai trò hệ thống không xóa được và không đổi phạm vi dữ liệu (nghiệp vụ dựa vào phạm vi đó).</li>
 *   <li>Vai trò {@code ADMIN} luôn giữ {@code role:manage} để không ai tự khóa mình khỏi màn phân quyền.</li>
 *   <li>Vai trò đang gán cho người dùng thì không xóa được.</li>
 * </ul>
 */
@Entity
@Table(name = "roles")
public class Role {

    public static final String ADMIN_CODE = "ADMIN";
    public static final String ROLE_MANAGE_PERMISSION = "role:manage";
    public static final int CODE_MAX_LENGTH = 50;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,49}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 20)
    private DataScope dataScope;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    /** Tạo vai trò tùy chỉnh (không phải vai trò hệ thống). Mã được chuẩn hóa về chữ hoa. */
    public static Role create(Long companyId, String code, String name, String description, DataScope dataScope,
                              Collection<Permission> permissions, OffsetDateTime now) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw RoleRuleException.invalidCode();
        }
        Role role = new Role();
        role.companyId = Objects.requireNonNull(companyId);
        role.code = normalized;
        role.system = false;
        role.apply(name, description, Objects.requireNonNull(dataScope), permissions, now);
        return role;
    }

    /** Sửa tên, mô tả, phạm vi dữ liệu và thay toàn bộ danh sách quyền. */
    public void update(String name, String description, DataScope dataScope, Collection<Permission> permissions,
                       OffsetDateTime now) {
        Objects.requireNonNull(dataScope);
        if (system && dataScope != this.dataScope) {
            throw RoleRuleException.systemRoleScopeLocked();
        }
        apply(name, description, dataScope, permissions, now);
    }

    /** Kiểm tra trước khi xóa. */
    public void ensureDeletable(long assignedUserCount) {
        if (system) {
            throw RoleRuleException.systemRoleNotDeletable();
        }
        if (assignedUserCount > 0) {
            throw RoleRuleException.roleInUse(assignedUserCount);
        }
    }

    public boolean grants(String permissionCode) {
        return permissions.stream().anyMatch(p -> p.getCode().equals(permissionCode));
    }

    public Set<String> permissionCodes() {
        Set<String> codes = new TreeSet<>();
        permissions.forEach(p -> codes.add(p.getCode()));
        return codes;
    }

    private void apply(String name, String description, DataScope dataScope, Collection<Permission> newPermissions,
                       OffsetDateTime now) {
        if (newPermissions == null || newPermissions.isEmpty()) {
            throw RoleRuleException.noPermission();
        }
        if (ADMIN_CODE.equals(code)
                && newPermissions.stream().noneMatch(p -> ROLE_MANAGE_PERMISSION.equals(p.getCode()))) {
            throw RoleRuleException.adminMustKeepRoleManage();
        }
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.dataScope = dataScope;
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
        this.updatedAt = now;
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

    public String getDescription() {
        return description;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public boolean isSystem() {
        return system;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
