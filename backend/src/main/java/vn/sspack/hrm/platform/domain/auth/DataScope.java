package vn.sspack.hrm.platform.domain.auth;

/** Phạm vi dữ liệu của vai trò (cột {@code roles.data_scope}). */
public enum DataScope {
    /** Toàn công ty. */
    ALL,
    /** Phòng ban mình quản lý và các phòng ban con. */
    DEPARTMENT_TREE,
    /** Chỉ phòng ban mình. */
    DEPARTMENT,
    /** Chỉ dữ liệu của bản thân. */
    SELF,
    /** Chỉ đối tượng được giao (vd ứng viên được giao phỏng vấn). */
    ASSIGNED
}
