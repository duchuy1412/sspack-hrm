package vn.sspack.hrm.platform.domain.auth;

/**
 * Trạng thái tài khoản do quản trị đặt. Khóa tạm thời vì đăng nhập sai không đổi trạng thái này mà dùng
 * {@code locked_until}.
 */
public enum UserStatus {
    ACTIVE,
    /** Quản trị khóa thủ công. */
    LOCKED,
    /** Vô hiệu hóa (vd nhân viên đã nghỉ việc, EMP-06). */
    DISABLED
}
