package vn.sspack.hrm.org.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeHeadcountService;
import vn.sspack.hrm.employee.application.EmployeeHeadcountService.Headcount;
import vn.sspack.hrm.employee.application.EmployeeRefService;
import vn.sspack.hrm.employee.application.EmployeeRefService.EmployeeRef;
import vn.sspack.hrm.org.domain.Department;
import vn.sspack.hrm.org.domain.DepartmentRuleException;
import vn.sspack.hrm.org.infrastructure.persistence.DepartmentRepository;
import vn.sspack.hrm.platform.application.audit.AuditEntity;
import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.application.rbac.DataScopeService;
import vn.sspack.hrm.shared.api.RequestActor;
import vn.sspack.hrm.shared.error.ApiException;
import vn.sspack.hrm.shared.security.DataScope;

/**
 * ORG-01: HR tạo cây phòng ban nhiều cấp, đổi cấp cha, ngừng hoạt động, xóa phòng ban chưa dùng.
 * Mọi thao tác ghi đều ghi audit log với giá trị trước/sau.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departments;
    private final EmployeeHeadcountService headcounts;
    private final EmployeeRefService employeeRefs;
    private final DepartmentHeadResolver headResolver;
    private final DataScopeService dataScopes;
    private final AuditLogService audit;
    private final Clock clock;

    public DepartmentService(DepartmentRepository departments, EmployeeHeadcountService headcounts,
                             EmployeeRefService employeeRefs, DepartmentHeadResolver headResolver,
                             DataScopeService dataScopes, AuditLogService audit, Clock clock) {
        this.departments = departments;
        this.headcounts = headcounts;
        this.employeeRefs = employeeRefs;
        this.headResolver = headResolver;
        this.dataScopes = dataScopes;
        this.audit = audit;
        this.clock = clock;
    }

    public record DepartmentView(Long id, Long parentId, String code, String name, String path, int depth,
                                 boolean active, int childCount, long employeeCount, long workingEmployeeCount,
                                 int version, OffsetDateTime updatedAt, PersonRef head, PersonRef effectiveApprover) {
    }

    /** Người được nhắc tới trên cây; {@code warnings} chỉ có giá trị với trưởng đơn vị (NO_ACCOUNT, NO_MANAGER_SCOPE). */
    public record PersonRef(Long employeeId, String code, String fullName, List<String> warnings) {
    }

    public record HeadCommand(Long employeeId, int version) {
    }

    public record CreateCommand(String code, String name, Long parentId) {
    }

    public record UpdateCommand(String code, String name, int version) {
    }

    public record MoveCommand(Long parentId, int version) {
    }

    public record StatusCommand(boolean active, int version) {
    }

    // ================================================================= đọc

    /** Mọi phòng ban theo thứ tự cây (cha trước con); ngừng hoạt động chỉ có khi {@code includeInactive}. */
    @Transactional(readOnly = true)
    public List<DepartmentView> list(long companyId, boolean includeInactive, boolean includeWarnings) {
        // Người duyệt hiệu lực tính trên toàn bộ phòng ban: cấp trên có thể đã ngừng hoạt động
        List<Department> company = departments.findByCompanyIdOrderByPathAsc(companyId);
        List<Department> shown = includeInactive ? company : company.stream().filter(Department::isActive).toList();
        Map<Long, Integer> children = new HashMap<>();
        shown.forEach(d -> {
            if (d.getParentId() != null) {
                children.merge(d.getParentId(), 1, Integer::sum);
            }
        });
        Map<Long, Headcount> counts = headcounts.countByDepartment(companyId,
                shown.stream().map(Department::getId).toList());
        return toViews(companyId, company, shown, children, counts, includeWarnings);
    }

    @Transactional(readOnly = true)
    public DepartmentView get(long companyId, long id, boolean includeWarnings) {
        return view(load(companyId, id), includeWarnings);
    }

    // ================================================================= ghi

    @Transactional
    public DepartmentView create(RequestActor actor, CreateCommand command) {
        Department parent = command.parentId() == null ? null
                : departments.findForUpdate(command.parentId(), actor.companyId())
                        .orElseThrow(DepartmentService::parentNotFound);
        Department department = Department.create(actor.companyId(), parent, command.code(), command.name(), now());
        if (departments.existsByCompanyIdAndCode(actor.companyId(), department.getCode())) {
            throw codeExists(department.getCode());
        }
        department = departments.saveAndFlush(department);
        department.completePath();
        departments.flush();
        audit.record(actor.companyId(), actor.userId(), Action.CREATE, AuditEntity.DEPARTMENTS, department.getId(),
                null, snapshot(department), actor.ipAddress());
        return view(department);
    }

    @Transactional
    public DepartmentView update(RequestActor actor, long id, UpdateCommand command) {
        Department department = load(actor.companyId(), id);
        checkVersion(department, command.version());
        Map<String, Object> before = snapshot(department);
        department.rename(command.code(), command.name(), now());
        if (departments.existsByCompanyIdAndCodeAndIdNot(actor.companyId(), department.getCode(), id)) {
            throw codeExists(department.getCode());
        }
        departments.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.DEPARTMENTS, id, before,
                snapshot(department), actor.ipAddress());
        return view(department);
    }

    /** Đổi cấp cha; path của cả nhánh con được cập nhật trong cùng transaction. */
    @Transactional
    public DepartmentView move(RequestActor actor, long id, MoveCommand command) {
        Long parentId = command.parentId();
        // Khóa theo thứ tự id để hai lệnh chuyển chéo nhau không khóa chết
        Department department;
        Department parent = null;
        if (parentId != null && parentId < id) {
            parent = departments.findForUpdate(parentId, actor.companyId()).orElseThrow(DepartmentService::parentNotFound);
            department = departments.findForUpdate(id, actor.companyId()).orElseThrow(DepartmentService::notFound);
        } else {
            department = departments.findForUpdate(id, actor.companyId()).orElseThrow(DepartmentService::notFound);
            if (parentId != null) {
                parent = departments.findForUpdate(parentId, actor.companyId())
                        .orElseThrow(DepartmentService::parentNotFound);
            }
        }
        checkVersion(department, command.version());
        Map<String, Object> before = snapshot(department);
        Optional<String> oldPath = department.moveUnder(parent, now());
        if (oldPath.isEmpty()) {
            return view(department);
        }
        Map<String, Object> after = snapshot(department);
        departments.flush();
        int descendants = departments.rewriteSubtreePaths(actor.companyId(), oldPath.get(), department.getPath(),
                now());
        after.put("movedDescendants", descendants);
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.DEPARTMENTS, id, before, after,
                actor.ipAddress());
        return view(load(actor.companyId(), id));
    }

    @Transactional
    public DepartmentView changeStatus(RequestActor actor, long id, StatusCommand command) {
        // Khóa dòng: nhân viên được gán vào phòng ban (EMP-01) cũng khóa dòng này, nên không lọt nhân viên mới
        // vào phòng ban đang bị ngừng hoạt động cùng lúc.
        Department department = departments.findForUpdate(id, actor.companyId())
                .orElseThrow(DepartmentService::notFound);
        checkVersion(department, command.version());
        if (department.isActive() == command.active()) {
            return view(department);
        }
        Map<String, Object> before = snapshot(department);
        if (command.active()) {
            Department parent = department.getParentId() == null ? null
                    : load(actor.companyId(), department.getParentId());
            department.activate(parent, now());
        } else {
            Headcount count = headcount(department);
            department.deactivate(departments.countByParentIdAndActiveTrue(id), count.working(), now());
        }
        departments.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.DEPARTMENTS, id, before,
                snapshot(department), actor.ipAddress());
        return view(department);
    }

    /** Chỉ định hoặc bỏ (null) trưởng đơn vị. Chỉ nhân viên đang làm việc của công ty mới được chọn. */
    @Transactional
    public DepartmentView assignHead(RequestActor actor, long id, HeadCommand command) {
        Department department = departments.findForUpdate(id, actor.companyId())
                .orElseThrow(DepartmentService::notFound);
        checkVersion(department, command.version());
        Long employeeId = command.employeeId();
        // Quy tắc miền và no-op xét trước khi tra nhân viên
        if (!department.wouldChangeHead(employeeId)) {
            return view(department);
        }
        if (employeeId != null) {
            EmployeeRef ref = employeeRefs.refs(actor.companyId(), List.of(employeeId)).get(employeeId);
            if (ref == null || !ref.working()) {
                throw DepartmentRuleException.headNotWorking();
            }
        }
        Map<String, Object> before = snapshot(department);
        if (!department.assignHead(employeeId, now())) {
            return view(department);
        }
        departments.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.DEPARTMENTS, id, before,
                snapshot(department), actor.ipAddress());
        return view(department);
    }

    @Transactional
    public void delete(RequestActor actor, long id) {
        Department department = load(actor.companyId(), id);
        department.ensureDeletable(departments.countByParentId(id), headcount(department).total());
        Map<String, Object> before = snapshot(department);
        try {
            departments.delete(department);
            departments.flush();
        } catch (DataIntegrityViolationException e) {
            // Còn bảng khác trỏ tới (phân ca, yêu cầu tuyển dụng...): giữ lại, đề nghị ngừng hoạt động
            throw new ApiException(HttpStatus.CONFLICT, "DEPARTMENT_IN_USE",
                    "Phòng ban đang được dùng ở dữ liệu khác nên không xóa được. Hãy chọn Ngừng hoạt động.");
        }
        audit.record(actor.companyId(), actor.userId(), Action.DELETE, AuditEntity.DEPARTMENTS, id, before, null,
                actor.ipAddress());
    }

    // ================================================================= nội bộ

    private Department load(long companyId, long id) {
        return departments.findByIdAndCompanyId(id, companyId).orElseThrow(DepartmentService::notFound);
    }

    private Headcount headcount(Department department) {
        return headcounts.countByDepartment(department.getCompanyId(), List.of(department.getId()))
                .get(department.getId());
    }

    /** Dùng cho các thao tác ghi (đều yêu cầu org:manage nên luôn kèm cảnh báo). */
    private DepartmentView view(Department department) {
        return view(department, true);
    }

    private DepartmentView view(Department department, boolean includeWarnings) {
        long companyId = department.getCompanyId();
        return toViews(companyId, departments.findByCompanyIdOrderByPathAsc(companyId), List.of(department),
                Map.of(department.getId(), (int) departments.countByParentId(department.getId())),
                Map.of(department.getId(), headcount(department)), includeWarnings).get(0);
    }

    /**
     * Dựng view cho {@code shown} (một phần của {@code company}); trưởng và người duyệt hiệu lực tra bằng một lần
     * đọc nhân viên, quyền của tài khoản trưởng tra một lần cho mỗi tài khoản.
     */
    private List<DepartmentView> toViews(long companyId, List<Department> company, List<Department> shown,
                                         Map<Long, Integer> children, Map<Long, Headcount> counts,
                                         boolean includeWarnings) {
        Map<Long, Optional<Long>> approvers = headResolver.effectiveApprovers(companyId, company);
        Set<Long> ids = new HashSet<>();
        for (Department d : shown) {
            if (d.getHeadEmployeeId() != null) {
                ids.add(d.getHeadEmployeeId());
            }
            approvers.getOrDefault(d.getId(), Optional.empty()).ifPresent(ids::add);
        }
        Map<Long, EmployeeRef> refs = employeeRefs.refs(companyId, ids);
        Map<Long, Optional<DataScope>> scopes = new HashMap<>();
        List<DepartmentView> views = new ArrayList<>(shown.size());
        for (Department d : shown) {
            EmployeeRef head = d.getHeadEmployeeId() == null ? null : refs.get(d.getHeadEmployeeId());
            PersonRef headRef = head == null ? null
                    : new PersonRef(head.id(), head.code(), head.fullName(), includeWarnings ? warnings(companyId, head, scopes) : List.of());
            EmployeeRef approver = approvers.getOrDefault(d.getId(), Optional.empty()).map(refs::get).orElse(null);
            PersonRef approverRef = approver == null ? null
                    : new PersonRef(approver.id(), approver.code(), approver.fullName(), List.of());
            Headcount c = counts.get(d.getId()) == null ? Headcount.NONE : counts.get(d.getId());
            views.add(new DepartmentView(d.getId(), d.getParentId(), d.getCode(), d.getName(), d.getPath(),
                    d.depth(), d.isActive(), children.getOrDefault(d.getId(), 0), c.total(), c.working(),
                    d.getVersion(), d.getUpdatedAt(), headRef, approverRef));
        }
        return views;
    }

    private List<String> warnings(long companyId, EmployeeRef head, Map<Long, Optional<DataScope>> scopes) {
        if (head.userId() == null) {
            return List.of("NO_ACCOUNT");
        }
        Optional<DataScope> scope = scopes.computeIfAbsent(head.userId(),
                userId -> dataScopes.widestScope(companyId, userId));
        boolean manager = scope.filter(s -> s == DataScope.DEPARTMENT_TREE || s == DataScope.ALL).isPresent();
        return manager ? List.of() : List.of("NO_MANAGER_SCOPE");
    }

    private static void checkVersion(Department department, int version) {
        if (department.getVersion() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại và thử lại.");
        }
    }

    private static Map<String, Object> snapshot(Department d) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", d.getCode());
        value.put("name", d.getName());
        value.put("parentId", d.getParentId());
        value.put("path", d.getPath());
        value.put("active", d.isActive());
        value.put("headEmployeeId", d.getHeadEmployeeId());
        return value;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "Không tìm thấy phòng ban.");
    }

    private static ApiException parentNotFound() {
        return new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_PARENT_NOT_FOUND",
                "Không tìm thấy phòng ban cấp trên.");
    }

    private static ApiException codeExists(String code) {
        return new ApiException(HttpStatus.CONFLICT, "DEPARTMENT_CODE_EXISTS", "Mã phòng ban " + code + " đã tồn tại.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
