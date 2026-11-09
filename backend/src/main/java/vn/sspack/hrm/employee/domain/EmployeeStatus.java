package vn.sspack.hrm.employee.domain;

import java.util.EnumSet;
import java.util.Set;

/** Trạng thái làm việc (cột {@code employees.status}). */
public enum EmployeeStatus {
    /** Thử việc. */
    PROBATION,
    /** Chính thức, đang làm việc. */
    ACTIVE,
    /** Nghỉ dài hạn (thai sản, nghỉ không lương...), vẫn còn quan hệ lao động. */
    ON_LEAVE,
    /** Đã nghỉ việc (EMP-06). */
    RESIGNED,
    /** Chấm dứt hợp đồng (EMP-06). */
    TERMINATED;

    /** Trạng thái còn đang làm việc. */
    public static final Set<EmployeeStatus> WORKING = EnumSet.of(PROBATION, ACTIVE, ON_LEAVE);

    /** Trạng thái chọn được khi tạo hồ sơ. */
    public static final Set<EmployeeStatus> ON_CREATE = EnumSet.of(PROBATION, ACTIVE);

    /** Nghỉ việc, chấm dứt: chỉ đặt qua quy trình nghỉ việc (EMP-06), không chọn trực tiếp khi sửa hồ sơ. */
    public boolean isLeft() {
        return this == RESIGNED || this == TERMINATED;
    }
}
