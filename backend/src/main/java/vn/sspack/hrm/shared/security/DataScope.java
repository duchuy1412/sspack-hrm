package vn.sspack.hrm.shared.security;

/**
 * Phạm vi dữ liệu của vai trò (cột {@code roles.data_scope}). Dùng chung cho mọi module vì module nào cũng phải
 * lọc dữ liệu theo phạm vi (CLAUDE.md: kiểm tra quyền chức năng VÀ phạm vi dữ liệu).
 */
public enum DataScope {
    /** Toàn công ty. */
    ALL(4),
    /** Phòng ban mình quản lý và các phòng ban con. */
    DEPARTMENT_TREE(3),
    /** Chỉ phòng ban mình. */
    DEPARTMENT(2),
    /** Chỉ dữ liệu của bản thân. */
    SELF(1),
    /** Chỉ đối tượng được giao (vd ứng viên được giao phỏng vấn). */
    ASSIGNED(0);

    private final int breadth;

    DataScope(int breadth) {
        this.breadth = breadth;
    }

    /** Phạm vi rộng hơn trong hai phạm vi; người có nhiều vai trò được xem theo vai trò rộng nhất. */
    public DataScope widest(DataScope other) {
        return other != null && other.breadth > breadth ? other : this;
    }
}
