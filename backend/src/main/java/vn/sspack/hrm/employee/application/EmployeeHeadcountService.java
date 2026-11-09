package vn.sspack.hrm.employee.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeHeadcountJdbcRepository;

/**
 * Điểm vào công khai của module employee cho module khác hỏi số nhân viên theo phòng ban (ORG-01: không xóa
 * phòng ban còn nhân viên) hoặc theo chức danh (ORG-02). Module org không đọc bảng {@code employees} trực tiếp.
 */
@Service
public class EmployeeHeadcountService {

    /**
     * @param total   số nhân viên từng thuộc phòng ban hoặc giữ chức danh (mọi trạng thái, kể cả đã nghỉ)
     * @param working số nhân viên đang làm việc (thử việc, chính thức, tạm nghỉ)
     */
    public record Headcount(long total, long working) {

        public static final Headcount NONE = new Headcount(0, 0);
    }

    private final EmployeeHeadcountJdbcRepository repository;

    public EmployeeHeadcountService(EmployeeHeadcountJdbcRepository repository) {
        this.repository = repository;
    }

    /** Số nhân viên của từng phòng ban; phòng ban không có ai cũng có mặt với {@link Headcount#NONE}. */
    @Transactional(readOnly = true)
    public Map<Long, Headcount> countByDepartment(long companyId, Collection<Long> departmentIds) {
        return toHeadcounts(departmentIds, repository.countByDepartment(companyId, departmentIds));
    }

    /** Số nhân viên của từng chức danh (ORG-02: không xóa chức danh đã có người giữ). */
    @Transactional(readOnly = true)
    public Map<Long, Headcount> countByPosition(long companyId, Collection<Long> positionIds) {
        return toHeadcounts(positionIds, repository.countByPosition(companyId, positionIds));
    }

    private static Map<Long, Headcount> toHeadcounts(Collection<Long> ids, Map<Long, long[]> raw) {
        Map<Long, Headcount> result = new HashMap<>();
        for (Long id : ids) {
            long[] c = raw.get(id);
            result.put(id, c == null ? Headcount.NONE : new Headcount(c[0], c[1]));
        }
        return result;
    }
}
