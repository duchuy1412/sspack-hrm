package vn.sspack.hrm.org.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeRefService;
import vn.sspack.hrm.employee.application.EmployeeRefService.EmployeeRef;
import vn.sspack.hrm.org.domain.ApproverChain;
import vn.sspack.hrm.org.domain.Department;
import vn.sspack.hrm.org.infrastructure.persistence.DepartmentRepository;

/**
 * Tìm người duyệt cấp 1 của nhân viên (ORG-04): trưởng đơn vị của phòng ban, leo lên cấp cha khi thiếu,
 * không bao giờ là chính người gửi. Điểm vào công khai cho phê duyệt (PLT-06) và cây phòng ban.
 */
@Service
public class DepartmentHeadResolver {

    /** Mã nhân viên không khớp ai: dùng làm người gửi giả cho "nhân viên thường" của phòng ban. */
    private static final long NO_REQUESTER = -1L;

    private final DepartmentRepository departments;
    private final EmployeeRefService employeeRefs;

    public DepartmentHeadResolver(DepartmentRepository departments, EmployeeRefService employeeRefs) {
        this.departments = departments;
        this.employeeRefs = employeeRefs;
    }

    /** @return mã nhân viên người duyệt cấp 1; rỗng nếu phòng ban không thuộc công ty hoặc không có ai phù hợp */
    @Transactional(readOnly = true)
    public Optional<Long> firstLevelApprover(long companyId, long departmentId, long requesterEmployeeId) {
        Optional<Department> own = departments.findByIdAndCompanyId(departmentId, companyId);
        if (own.isEmpty()) {
            return Optional.empty();
        }
        List<Long> chainIds = own.get().ancestorIdsFromSelf();
        Map<Long, Department> byId = departments.findByCompanyIdAndIdIn(companyId, chainIds).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
        List<Long> heads = headsAlong(chainIds, byId);
        return ApproverChain.pick(heads, requesterEmployeeId, workingAmong(companyId, heads));
    }

    /**
     * Người duyệt mà một nhân viên thường của từng phòng ban sẽ có. Chuỗi trưởng tính trên {@code all}
     * (toàn bộ phòng ban của công ty) và chỉ đọc nhân viên một lần.
     */
    @Transactional(readOnly = true)
    public Map<Long, Optional<Long>> effectiveApprovers(long companyId, List<Department> all) {
        Map<Long, Department> byId = new HashMap<>();
        all.forEach(d -> byId.put(d.getId(), d));
        Map<Long, List<Long>> headsByDepartment = new LinkedHashMap<>();
        Set<Long> allHeads = new HashSet<>();
        for (Department d : all) {
            List<Long> heads = headsAlong(d.ancestorIdsFromSelf(), byId);
            headsByDepartment.put(d.getId(), heads);
            heads.stream().filter(h -> h != null).forEach(allHeads::add);
        }
        Set<Long> working = workingAmong(companyId, allHeads);
        Map<Long, Optional<Long>> result = new LinkedHashMap<>();
        headsByDepartment.forEach((id, heads) -> result.put(id, ApproverChain.pick(heads, NO_REQUESTER, working)));
        return result;
    }

    private static List<Long> headsAlong(List<Long> chainIds, Map<Long, Department> byId) {
        List<Long> heads = new ArrayList<>(chainIds.size());
        for (Long id : chainIds) {
            Department d = byId.get(id);
            heads.add(d == null ? null : d.getHeadEmployeeId());
        }
        return heads;
    }

    private Set<Long> workingAmong(long companyId, Collection<Long> heads) {
        Set<Long> ids = heads.stream().filter(h -> h != null).collect(Collectors.toSet());
        return employeeRefs.refs(companyId, ids).values().stream()
                .filter(EmployeeRef::working).map(EmployeeRef::id).collect(Collectors.toSet());
    }
}
