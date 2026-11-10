package vn.sspack.hrm.employee.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeAccess.Visibility;
import vn.sspack.hrm.employee.domain.EmployeeStatus;
import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeRepository;

/**
 * Điểm vào công khai của module employee cho thông tin "danh bạ" của nhân viên đang làm việc: chỉ thông tin
 * công việc (họ tên, chức danh, phòng ban, email công việc), không có thông tin cá nhân. Dùng cho sơ đồ tổ chức
 * (ORG-03) và sau này là danh bạ công ty (ESS-03); mọi người dùng xem được cả công ty.
 */
@Service
public class EmployeeDirectoryService {

    /**
     * Thẻ nhân viên.
     *
     * @param profileVisible người xem mở được hồ sơ đầy đủ (nằm trong phạm vi dữ liệu của họ)
     * @param self           thẻ của chính người xem
     */
    public record DirectoryCard(Long id, String code, String fullName, Long departmentId, Long positionId,
                                String workEmail, boolean profileVisible, boolean self) {
    }

    private final EmployeeRepository employees;
    private final EmployeeAccess access;

    public EmployeeDirectoryService(EmployeeRepository employees, EmployeeAccess access) {
        this.employees = employees;
        this.access = access;
    }

    /** Nhân viên đang làm việc (thử việc, chính thức, nghỉ dài hạn) của công ty, chưa sắp xếp. */
    @Transactional(readOnly = true)
    public List<DirectoryCard> workingEmployees(long companyId, long viewerUserId) {
        Visibility visibility = access.resolve(companyId, viewerUserId);
        return employees.findByCompanyIdAndStatusIn(companyId, EmployeeStatus.WORKING).stream()
                .map(e -> new DirectoryCard(e.getId(), e.getCode(), e.getFullName(), e.getDepartmentId(),
                        e.getPositionId(), e.getWorkEmail(), visibility.covers(e), visibility.isSelf(e)))
                .toList();
    }
}
