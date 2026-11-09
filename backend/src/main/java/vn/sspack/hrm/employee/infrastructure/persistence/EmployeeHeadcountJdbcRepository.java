package vn.sspack.hrm.employee.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Đếm nhân viên theo phòng ban hoặc chức danh trực tiếp trên bảng {@code employees}. */
@Repository
public class EmployeeHeadcountJdbcRepository {

    /** Trạng thái còn đang làm việc (chưa nghỉ việc). */
    static final String WORKING_STATUSES = "('PROBATION', 'ACTIVE', 'ON_LEAVE')";

    private final JdbcClient jdbc;

    public EmployeeHeadcountJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Mỗi phòng ban: [tổng số nhân viên từng thuộc, số đang làm việc]. Phòng ban không có ai thì vắng mặt. */
    public Map<Long, long[]> countByDepartment(long companyId, Collection<Long> departmentIds) {
        return countBy("department_id", companyId, departmentIds);
    }

    /** Mỗi chức danh: [tổng số nhân viên từng giữ, số đang làm việc]. Chức danh không có ai thì vắng mặt. */
    public Map<Long, long[]> countByPosition(long companyId, Collection<Long> positionIds) {
        return countBy("position_id", companyId, positionIds);
    }

    /** {@code column} chỉ nhận hằng trong lớp này, không bao giờ từ dữ liệu người dùng. */
    private Map<Long, long[]> countBy(String column, long companyId, Collection<Long> ids) {
        Map<Long, long[]> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        jdbc.sql("""
                        SELECT %1$s AS group_id,
                               count(*) AS total,
                               count(*) FILTER (WHERE status IN %2$s) AS working
                        FROM hrm.employees
                        WHERE company_id = :companyId AND %1$s IN (:ids)
                        GROUP BY %1$s
                        """.formatted(column, WORKING_STATUSES))
                .param("companyId", companyId)
                .param("ids", ids)
                .query(rs -> {
                    counts.put(rs.getLong("group_id"), new long[] {rs.getLong("total"), rs.getLong("working")});
                });
        return counts;
    }
}
