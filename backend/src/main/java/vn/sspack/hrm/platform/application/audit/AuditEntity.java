package vn.sspack.hrm.platform.application.audit;

/**
 * Đối tượng được ghi nhật ký, đặt theo tên bảng thật (cột {@code audit_logs.entity_type}) để trả lời
 * "bảng nào" trong PLT-04. Module mới thêm hằng ở đây khi bắt đầu ghi audit cho bảng của mình.
 */
public enum AuditEntity {
    USERS("users"),
    ROLES("roles"),
    REFRESH_TOKENS("refresh_tokens"),
    DEPARTMENTS("departments"),
    JOB_GRADES("job_grades"),
    POSITIONS("positions");

    private final String table;

    AuditEntity(String table) {
        this.table = table;
    }

    public String table() {
        return table;
    }

    public static boolean isKnownTable(String table) {
        for (AuditEntity entity : values()) {
            if (entity.table.equals(table)) {
                return true;
            }
        }
        return false;
    }
}
