package vn.sspack.hrm.employee.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.domain.EmployeeStatus;
import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeRepository;

/**
 * Điểm vào công khai của module employee để module khác tra thông tin tối thiểu của nhân viên theo mã
 * (ORG-04: trưởng đơn vị, người duyệt). Không phụ thuộc module khác.
 */
@Service
public class EmployeeRefService {

    /** Tham chiếu nhân viên; {@code working}: đang làm việc (thử việc, chính thức, nghỉ dài hạn). */
    public record EmployeeRef(Long id, String code, String fullName, Long departmentId, Long userId,
                              boolean working) {
    }

    private final EmployeeRepository employees;

    public EmployeeRefService(EmployeeRepository employees) {
        this.employees = employees;
    }

    /** Tham chiếu theo mã nhân viên; mã không thuộc công ty bị bỏ qua. */
    @Transactional(readOnly = true)
    public Map<Long, EmployeeRef> refs(long companyId, Collection<Long> ids) {
        Map<Long, EmployeeRef> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        employees.findByCompanyIdAndIdIn(companyId, new HashSet<>(ids)).forEach(e -> result.put(e.getId(),
                new EmployeeRef(e.getId(), e.getCode(), e.getFullName(), e.getDepartmentId(), e.getUserId(),
                        EmployeeStatus.WORKING.contains(e.getStatus()))));
        return result;
    }
}
