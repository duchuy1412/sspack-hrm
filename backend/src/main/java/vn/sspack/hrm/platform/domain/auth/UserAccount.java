package vn.sspack.hrm.platform.domain.auth;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
 * Tài khoản đăng nhập (bảng {@code users}). Chứa quy tắc khóa tạm thời khi đăng nhập sai (PLT-01).
 * Họ tên nằm ở hồ sơ nhân viên (module employee), không ở đây.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    public static final String USER_MANAGE_PERMISSION = "user:manage";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "preferred_locale", nullable = false, length = 5)
    private String preferredLocale = "vi";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

    /** Dùng cho test và seed: tạo tài khoản mới đang hoạt động. */
    public static UserAccount create(Long companyId, String email, String passwordHash, OffsetDateTime now) {
        UserAccount user = new UserAccount();
        user.companyId = companyId;
        user.email = email;
        user.passwordHash = passwordHash;
        user.updatedAt = now;
        return user;
    }

    // ---------------------------------------------------------------- quy tắc nghiệp vụ

    /** Còn trong thời gian khóa tạm thời do đăng nhập sai. */
    public boolean isTemporarilyLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Thời gian khóa còn lại, nếu đang bị khóa. */
    public Optional<Duration> remainingLock(OffsetDateTime now) {
        return isTemporarilyLocked(now) ? Optional.of(Duration.between(now, lockedUntil)) : Optional.empty();
    }

    /** Tài khoản được quản trị cho phép đăng nhập (không bị khóa thủ công hay vô hiệu hóa). */
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Ghi nhận một lần nhập sai mật khẩu. Đủ số lần cho phép thì khóa tạm thời và đặt lại bộ đếm,
     * để sau khi hết khóa người dùng lại có đủ số lần thử.
     *
     * @return {@code true} nếu lần sai này làm tài khoản bị khóa
     */
    public boolean registerFailedLogin(OffsetDateTime now, LoginLockPolicy policy) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            lockedUntil = null; // lần khóa trước đã hết hạn
        }
        failedLoginCount++;
        updatedAt = now;
        if (failedLoginCount >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    /** Đăng nhập thành công: xóa bộ đếm sai và mốc khóa, ghi thời điểm đăng nhập. */
    public void registerSuccessfulLogin(OffsetDateTime now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    /** Mã vai trò, sắp xếp ổn định. */
    public Set<String> roleCodes() {
        Set<String> codes = new TreeSet<>();
        roles.forEach(r -> codes.add(r.getCode()));
        return codes;
    }

    /** Hợp các quyền của mọi vai trò, sắp xếp ổn định. */
    public Set<String> permissionCodes() {
        Set<String> codes = new TreeSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> codes.add(p.getCode())));
        return codes;
    }

    /**
     * Thay toàn bộ vai trò của tài khoản (PLT-03). Phải còn ít nhất một vai trò; người tự sửa chính mình
     * không được gỡ quyền quản lý tài khoản của bản thân (tránh khóa mình ra ngoài).
     */
    public void assignRoles(Collection<Role> newRoles, boolean actingOnSelf, OffsetDateTime now) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw RoleRuleException.userMustHaveRole();
        }
        if (actingOnSelf && permissionCodes().contains(USER_MANAGE_PERMISSION)
                && newRoles.stream().noneMatch(r -> r.grants(USER_MANAGE_PERMISSION))) {
            throw RoleRuleException.cannotRemoveOwnUserManage();
        }
        roles.clear();
        roles.addAll(newRoles);
        updatedAt = now;
    }

    // ---------------------------------------------------------------- getter

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public int getVersion() {
        return version;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
