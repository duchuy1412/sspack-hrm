package vn.sspack.hrm.employee.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import vn.sspack.hrm.employee.domain.Employee;
import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeRepository;
import vn.sspack.hrm.org.application.OrgDirectory;
import vn.sspack.hrm.platform.application.rbac.DataScopeService;
import vn.sspack.hrm.shared.security.DataScope;

/**
 * Quy phạm vi dữ liệu của người xem ra tập hồ sơ được thấy (CLAUDE.md: lọc theo phạm vi, không chỉ ẩn ở giao
 * diện).
 *
 * <ul>
 *   <li>ALL: cả công ty.</li>
 *   <li>DEPARTMENT_TREE: phòng ban trong hồ sơ của chính người xem và mọi phòng ban cấp dưới. Tạm thời cho đến
 *       ORG-04; khi có trưởng đơn vị sẽ đổi thành "các phòng ban mình làm trưởng".</li>
 *   <li>DEPARTMENT: chỉ phòng ban của người xem.</li>
 *   <li>SELF, ASSIGNED: chỉ hồ sơ của chính mình.</li>
 * </ul>
 * Tài khoản không gắn hồ sơ nhân viên mà phạm vi hẹp hơn ALL thì không thấy hồ sơ nào.
 */
@Component
class EmployeeAccess {

    /**
     * Tập hồ sơ được thấy.
     *
     * @param all           thấy cả công ty
     * @param departmentIds phòng ban được thấy (khi không phải {@code all})
     * @param selfId        hồ sơ của chính người xem, null nếu tài khoản không gắn hồ sơ
     */
    record Visibility(boolean all, Set<Long> departmentIds, Long selfId) {

        static final Visibility NONE = new Visibility(false, Set.of(), null);

        boolean covers(Employee employee) {
            return all || departmentIds.contains(employee.getDepartmentId()) || employee.getId().equals(selfId);
        }

        boolean coversDepartment(long departmentId) {
            return all || departmentIds.contains(departmentId);
        }

        boolean isSelf(Employee employee) {
            return employee.getId().equals(selfId);
        }
    }

    private final DataScopeService scopes;
    private final EmployeeRepository employees;
    private final OrgDirectory org;

    EmployeeAccess(DataScopeService scopes, EmployeeRepository employees, OrgDirectory org) {
        this.scopes = scopes;
        this.employees = employees;
        this.org = org;
    }

    Visibility resolve(long companyId, long userId) {
        Optional<DataScope> scope = scopes.widestScope(companyId, userId);
        Optional<Employee> self = employees.findByCompanyIdAndUserId(companyId, userId);
        Long selfId = self.map(Employee::getId).orElse(null);
        if (scope.isEmpty()) {
            return Visibility.NONE;
        }
        return switch (scope.get()) {
            case ALL -> new Visibility(true, Set.of(), selfId);
            case DEPARTMENT_TREE -> self
                    .map(e -> new Visibility(false, Set.copyOf(org.subtreeIds(companyId, e.getDepartmentId())), selfId))
                    .orElse(Visibility.NONE);
            case DEPARTMENT -> self
                    .map(e -> new Visibility(false, Set.copyOf(List.of(e.getDepartmentId())), selfId))
                    .orElse(Visibility.NONE);
            case SELF, ASSIGNED -> new Visibility(false, Set.of(), selfId);
        };
    }
}
