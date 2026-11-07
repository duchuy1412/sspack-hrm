package vn.sspack.hrm.employee.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Đếm nhân viên theo phòng ban trực tiếp trên bảng {@code employees}. */
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
        Map<Long, long[]> counts = new HashMap<>();
        if (departmentIds.isEmpty()) {
            return counts;
        }
        jdbc.sql("""
                        SELECT department_id,
                               count(*) AS total,
                               count(*) FILTER (WHERE status IN %s) AS working
                        FROM hrm.employees
                        WHERE company_id = :companyId AND department_id IN (:ids)
                        GROUP BY department_id
                        """.formatted(WORKING_STATUSES))
                .param("companyId", companyId)
                .param("ids", departmentIds)
                .query(rs -> {
                    counts.put(rs.getLong("department_id"), new long[] {rs.getLong("total"), rs.getLong("working")});
                });
        return counts;
    }
}
