package vn.sspack.hrm.employee.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeAccess.Visibility;
import vn.sspack.hrm.employee.domain.Employee;
import vn.sspack.hrm.employee.domain.Employee.Placement;
import vn.sspack.hrm.employee.domain.Employee.Profile;
import vn.sspack.hrm.employee.domain.EmployeeStatus;
import vn.sspack.hrm.employee.domain.Gender;
import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeRepository;
import vn.sspack.hrm.employee.infrastructure.persistence.EmployeeSpecifications;
import vn.sspack.hrm.org.application.OrgDirectory;
import vn.sspack.hrm.org.application.OrgDirectory.DepartmentRef;
import vn.sspack.hrm.org.application.OrgDirectory.PositionRef;
import vn.sspack.hrm.platform.application.audit.AuditEntity;
import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.shared.api.RequestActor;
import vn.sspack.hrm.shared.error.ApiException;

/**
 * EMP-01 (phần lõi): HR tạo và sửa hồ sơ nhân viên gồm thông tin cá nhân, liên hệ, công việc. Mọi lần đọc đều
 * lọc theo phạm vi dữ liệu của người xem ({@link EmployeeAccess}); thông tin cá nhân (ngày sinh, email cá nhân,
 * điện thoại, địa chỉ) chỉ trả cho người có quyền sửa hồ sơ hoặc chính chủ.
 */
@Service
public class EmployeeService {

    /** Tiền tố mã gợi ý khi tạo mới (SP0001, SP0002...). HR vẫn sửa được mã. */
    static final String CODE_PREFIX = "SP";

    private final EmployeeRepository employees;
    private final EmployeeAccess access;
    private final OrgDirectory org;
    private final AuditLogService audit;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, EmployeeAccess access, OrgDirectory org,
                           AuditLogService audit, Clock clock) {
        this.employees = employees;
        this.access = access;
        this.org = org;
        this.audit = audit;
        this.clock = clock;
    }

    /** Người xem: tài khoản và việc có quyền {@code employee:manage} hay không (lấy từ token). */
    public record Viewer(long companyId, long userId, boolean canManage) {
    }

    /** Bộ lọc danh sách; trường null hoặc rỗng là không lọc. Lọc phòng ban gồm cả phòng ban cấp dưới. */
    public record Filter(String q, Long departmentId, Set<EmployeeStatus> statuses) {
    }

    public record EmployeeSummary(Long id, String code, String fullName, String workEmail, Long departmentId,
                                  String departmentName, Long positionId, String positionName, LocalDate hireDate,
                                  EmployeeStatus status) {
    }

    /**
     * Hồ sơ đầy đủ. Khi {@code personalVisible} là false, các trường cá nhân là null dù có dữ liệu.
     *
     * @param editable        người xem sửa được hồ sơ này
     * @param allowedStatuses trạng thái chọn được khi sửa
     */
    public record EmployeeDetail(Long id, String code, String fullName, Gender gender, LocalDate dateOfBirth,
                                 String workEmail, String personalEmail, String phone, String address,
                                 Long departmentId, String departmentName, boolean departmentActive,
                                 Long positionId, String positionName, boolean positionActive, LocalDate hireDate,
                                 EmployeeStatus status, boolean personalVisible, boolean accountLinked,
                                 boolean editable, Set<EmployeeStatus> allowedStatuses, int version,
                                 OffsetDateTime updatedAt) {
    }

    public record SaveCommand(String code, String fullName, Gender gender, LocalDate dateOfBirth, String workEmail,
                              String personalEmail, String phone, String address, Long departmentId,
                              Long positionId, LocalDate hireDate, EmployeeStatus status) {
    }

    public record UpdateCommand(SaveCommand data, int version) {
    }

    // ================================================================= đọc

    @Transactional(readOnly = true)
    public Page<EmployeeSummary> list(Viewer viewer, Filter filter, Pageable pageable) {
        Visibility visibility = access.resolve(viewer.companyId(), viewer.userId());
        Specification<Employee> spec = EmployeeSpecifications.inCompany(viewer.companyId())
                .and(visible(visibility));
        if (filter.q() != null && !filter.q().isBlank()) {
            spec = spec.and(EmployeeSpecifications.matches(filter.q()));
        }
        if (filter.departmentId() != null) {
            spec = spec.and(EmployeeSpecifications.inDepartments(
                    org.subtreeIds(viewer.companyId(), filter.departmentId())));
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            spec = spec.and(EmployeeSpecifications.hasStatus(filter.statuses()));
        }
        Page<Employee> page = employees.findAll(spec, pageable);
        Map<Long, DepartmentRef> departments = org.departments(viewer.companyId(),
                ids(page.getContent().stream().map(Employee::getDepartmentId).toList()));
        Map<Long, PositionRef> positions = org.positions(viewer.companyId(),
                ids(page.getContent().stream().map(Employee::getPositionId).toList()));
        return page.map(e -> new EmployeeSummary(e.getId(), e.getCode(), e.getFullName(), e.getWorkEmail(),
                e.getDepartmentId(), nameOf(departments.get(e.getDepartmentId())), e.getPositionId(),
                e.getPositionId() == null ? null : nameOf(positions.get(e.getPositionId())), e.getHireDate(),
                e.getStatus()));
    }

    @Transactional(readOnly = true)
    public EmployeeDetail get(Viewer viewer, long id) {
        Visibility visibility = access.resolve(viewer.companyId(), viewer.userId());
        return detail(loadVisible(viewer.companyId(), id, visibility), viewer, visibility);
    }

    /** Mã gợi ý cho hồ sơ mới: số lớn nhất đang có cộng một, tối thiểu bốn chữ số (SP0026). */
    @Transactional(readOnly = true)
    public String nextCode(long companyId) {
        long next = employees.maxNumericCode(companyId, CODE_PREFIX) + 1;
        return CODE_PREFIX + String.format("%04d", next);
    }

    // ================================================================= ghi

    @Transactional
    public EmployeeDetail create(RequestActor actor, boolean canManage, SaveCommand command) {
        Visibility visibility = access.resolve(actor.companyId(), actor.userId());
        Placement placement = placement(actor.companyId(), command, visibility);
        checkUnique(actor.companyId(), command, null);
        Employee employee = Employee.create(actor.companyId(), profile(command), placement, today(),
                actor.userId(), now());
        employee = save(employee);
        audit.record(actor.companyId(), actor.userId(), Action.CREATE, AuditEntity.EMPLOYEES, employee.getId(), null,
                snapshot(employee), actor.ipAddress());
        return detail(employee, new Viewer(actor.companyId(), actor.userId(), canManage), visibility);
    }

    @Transactional
    public EmployeeDetail update(RequestActor actor, boolean canManage, long id, UpdateCommand command) {
        Visibility visibility = access.resolve(actor.companyId(), actor.userId());
        Employee employee = loadVisible(actor.companyId(), id, visibility);
        if (employee.getVersion() != command.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Hồ sơ vừa được người khác sửa. Hãy tải lại trang rồi thử lại.");
        }
        Placement placement = placement(actor.companyId(), command.data(), visibility);
        // Kiểm trùng trước khi sửa entity: truy vấn sau khi sửa sẽ làm Hibernate flush sớm.
        checkUnique(actor.companyId(), command.data(), id);
        Map<String, Object> before = snapshot(employee);
        employee.update(profile(command.data()), placement, today(), actor.userId(), now());
        employee = save(employee);
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.EMPLOYEES, id, before,
                snapshot(employee), actor.ipAddress());
        return detail(employee, new Viewer(actor.companyId(), actor.userId(), canManage), visibility);
    }

    // ================================================================= nội bộ

    private static Specification<Employee> visible(Visibility visibility) {
        if (visibility.all()) {
            return (root, query, cb) -> cb.conjunction();
        }
        Specification<Employee> byDepartment = EmployeeSpecifications.inDepartments(visibility.departmentIds());
        return visibility.selfId() == null ? byDepartment
                : byDepartment.or(EmployeeSpecifications.hasId(visibility.selfId()));
    }

    /** Hồ sơ ngoài phạm vi trả 404 như không tồn tại, để không lộ việc hồ sơ đó có hay không. */
    private Employee loadVisible(long companyId, long id, Visibility visibility) {
        return employees.findByIdAndCompanyId(id, companyId)
                .filter(visibility::covers)
                .orElseThrow(EmployeeService::notFound);
    }

    /** Khóa phòng ban (chống ngừng hoạt động cùng lúc) và kiểm phòng ban nằm trong phạm vi của người ghi. */
    private Placement placement(long companyId, SaveCommand command, Visibility visibility) {
        DepartmentRef department = org.lockDepartment(companyId, command.departmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "EMPLOYEE_DEPARTMENT_NOT_FOUND",
                        "Không tìm thấy phòng ban."));
        if (!visibility.coversDepartment(department.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_OUT_OF_SCOPE",
                    "Phòng ban nằm ngoài phạm vi dữ liệu bạn được quản lý.");
        }
        PositionRef position = org.position(companyId, command.positionId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "EMPLOYEE_POSITION_NOT_FOUND",
                        "Không tìm thấy chức danh."));
        return new Placement(department.id(), department.active(), position.id(), position.active());
    }

    private void checkUnique(long companyId, SaveCommand command, Long id) {
        String code = Employee.normalizeCode(command.code());
        boolean codeTaken = id == null
                ? employees.existsByCompanyIdAndCode(companyId, code)
                : employees.existsByCompanyIdAndCodeAndIdNot(companyId, code, id);
        if (codeTaken) {
            throw codeExists(code);
        }
        String email = Employee.normalizeEmail(command.workEmail());
        if (email != null && (id == null
                ? employees.existsByCompanyIdAndWorkEmail(companyId, email)
                : employees.existsByCompanyIdAndWorkEmailAndIdNot(companyId, email, id))) {
            throw workEmailExists(email);
        }
    }

    /** Lưu và flush; hai người lưu trùng mã hoặc email cùng lúc thì ràng buộc UNIQUE trong CSDL chặn lại. */
    private Employee save(Employee employee) {
        try {
            return employees.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("uq_employees_work_email")) {
                throw workEmailExists(employee.getWorkEmail());
            }
            if (message.contains("employee_code")) {
                throw codeExists(employee.getCode());
            }
            throw e;
        }
    }

    private EmployeeDetail detail(Employee e, Viewer viewer, Visibility visibility) {
        DepartmentRef department = org.departments(viewer.companyId(), List.of(e.getDepartmentId()))
                .get(e.getDepartmentId());
        PositionRef position = e.getPositionId() == null ? null
                : org.position(viewer.companyId(), e.getPositionId()).orElse(null);
        boolean editable = viewer.canManage() && visibility.covers(e);
        boolean personal = editable || visibility.isSelf(e);
        return new EmployeeDetail(e.getId(), e.getCode(), e.getFullName(), e.getGender(),
                personal ? e.getDateOfBirth() : null, e.getWorkEmail(), personal ? e.getPersonalEmail() : null,
                personal ? e.getPhone() : null, personal ? e.getAddress() : null, e.getDepartmentId(),
                nameOf(department), department != null && department.active(), e.getPositionId(),
                position == null ? null : position.name(), position != null && position.active(), e.getHireDate(),
                e.getStatus(), personal, e.getUserId() != null, editable, Employee.allowedOnUpdate(e.getStatus()),
                e.getVersion(), e.getUpdatedAt());
    }

    private static Profile profile(SaveCommand c) {
        return new Profile(c.code(), c.fullName(), c.gender(), c.dateOfBirth(), c.workEmail(), c.personalEmail(),
                c.phone(), c.address(), c.hireDate(), c.status());
    }

    private static Map<String, Object> snapshot(Employee e) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", e.getCode());
        value.put("fullName", e.getFullName());
        value.put("gender", e.getGender() == null ? null : e.getGender().name());
        value.put("dateOfBirth", e.getDateOfBirth() == null ? null : e.getDateOfBirth().toString());
        value.put("workEmail", e.getWorkEmail());
        value.put("personalEmail", e.getPersonalEmail());
        value.put("phone", e.getPhone());
        value.put("address", e.getAddress());
        value.put("departmentId", e.getDepartmentId());
        value.put("positionId", e.getPositionId());
        value.put("hireDate", e.getHireDate().toString());
        value.put("status", e.getStatus().name());
        return value;
    }

    private static Collection<Long> ids(List<Long> values) {
        Set<Long> result = new HashSet<>(values);
        result.remove(null);
        return result;
    }

    private static String nameOf(DepartmentRef ref) {
        return ref == null ? null : ref.name();
    }

    private static String nameOf(PositionRef ref) {
        return ref == null ? null : ref.name();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên.");
    }

    private static ApiException codeExists(String code) {
        return new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_CODE_EXISTS", "Mã nhân viên " + code + " đã tồn tại.");
    }

    private static ApiException workEmailExists(String email) {
        return new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_WORK_EMAIL_EXISTS",
                "Email công việc " + email + " đã được dùng cho nhân viên khác.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
