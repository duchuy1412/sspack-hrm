package vn.sspack.hrm.org.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeHeadcountService;
import vn.sspack.hrm.employee.application.EmployeeHeadcountService.Headcount;
import vn.sspack.hrm.org.domain.JobGrade;
import vn.sspack.hrm.org.domain.Position;
import vn.sspack.hrm.org.infrastructure.persistence.JobGradeRepository;
import vn.sspack.hrm.org.infrastructure.persistence.PositionRepository;
import vn.sspack.hrm.platform.application.audit.AuditEntity;
import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.shared.api.RequestActor;
import vn.sspack.hrm.shared.error.ApiException;

/**
 * ORG-02: HR quản lý danh mục chức danh, mỗi chức danh gắn với một cấp bậc. Mọi người dùng có {@code org:read}
 * xem được danh sách (không kèm khung lương). Mọi thao tác ghi đều ghi audit log với giá trị trước/sau.
 */
@Service
public class PositionService {

    private final PositionRepository positions;
    private final JobGradeRepository grades;
    private final EmployeeHeadcountService headcounts;
    private final AuditLogService audit;
    private final Clock clock;

    public PositionService(PositionRepository positions, JobGradeRepository grades,
                           EmployeeHeadcountService headcounts, AuditLogService audit, Clock clock) {
        this.positions = positions;
        this.grades = grades;
        this.headcounts = headcounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Chức danh kèm mã, tên cấp bậc; không có khung lương vì người chỉ có quyền xem cũng đọc được. */
    public record PositionView(Long id, String code, String name, Long jobGradeId, String jobGradeCode,
                               String jobGradeName, boolean jobGradeActive, boolean active, long employeeCount,
                               long workingEmployeeCount, int version, OffsetDateTime updatedAt) {
    }

    /** Bộ lọc danh sách; trường null là không lọc. */
    public record Filter(String q, Long jobGradeId, boolean includeInactive) {
    }

    public record SaveCommand(String code, String name, Long jobGradeId) {
    }

    public record UpdateCommand(String code, String name, Long jobGradeId, int version) {
    }

    public record StatusCommand(boolean active, int version) {
    }

    // ================================================================= đọc

    /**
     * Danh sách theo mã. Danh mục chỉ vài chục dòng nên lọc trong bộ nhớ và không phân trang; frontend cần cả
     * danh sách cho ô chọn chức danh.
     */
    @Transactional(readOnly = true)
    public List<PositionView> list(long companyId, Filter filter) {
        Map<Long, JobGrade> gradeById = grades.findByCompanyIdOrderByMinSalaryAscMaxSalaryAscCodeAsc(companyId)
                .stream().collect(Collectors.toMap(JobGrade::getId, Function.identity()));
        String q = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
        List<Position> matched = positions.findByCompanyIdOrderByCodeAsc(companyId).stream()
                .filter(p -> filter.includeInactive() || p.isActive())
                .filter(p -> filter.jobGradeId() == null || filter.jobGradeId().equals(p.getJobGradeId()))
                .filter(p -> q.isEmpty() || p.getCode().toLowerCase(Locale.ROOT).contains(q)
                        || p.getName().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        Map<Long, Headcount> counts = headcounts.countByPosition(companyId,
                matched.stream().map(Position::getId).toList());
        return matched.stream()
                .map(p -> toView(p, gradeById.get(p.getJobGradeId()), counts.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionView get(long companyId, long id) {
        return view(load(companyId, id));
    }

    // ================================================================= ghi

    @Transactional
    public PositionView create(RequestActor actor, SaveCommand command) {
        JobGrade grade = lockGrade(actor.companyId(), command.jobGradeId());
        Position position = Position.create(actor.companyId(), command.code(), command.name(), grade, now());
        if (positions.existsByCompanyIdAndCode(actor.companyId(), position.getCode())) {
            throw codeExists(position.getCode());
        }
        position = positions.saveAndFlush(position);
        audit.record(actor.companyId(), actor.userId(), Action.CREATE, AuditEntity.POSITIONS, position.getId(), null,
                snapshot(position), actor.ipAddress());
        return view(position);
    }

    @Transactional
    public PositionView update(RequestActor actor, long id, UpdateCommand command) {
        JobGrade grade = lockGrade(actor.companyId(), command.jobGradeId());
        Position position = load(actor.companyId(), id);
        JobGradeService.checkVersion(position.getVersion(), command.version());
        Map<String, Object> before = snapshot(position);
        position.update(command.code(), command.name(), grade, now());
        if (positions.existsByCompanyIdAndCodeAndIdNot(actor.companyId(), position.getCode(), id)) {
            throw codeExists(position.getCode());
        }
        positions.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.POSITIONS, id, before,
                snapshot(position), actor.ipAddress());
        return view(position);
    }

    @Transactional
    public PositionView changeStatus(RequestActor actor, long id, StatusCommand command) {
        Position position = load(actor.companyId(), id);
        JobGradeService.checkVersion(position.getVersion(), command.version());
        if (position.isActive() == command.active()) {
            return view(position);
        }
        Map<String, Object> before = snapshot(position);
        if (command.active()) {
            position.activate(lockGrade(actor.companyId(), position.getJobGradeId()), now());
        } else {
            position.deactivate(now());
        }
        positions.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.POSITIONS, id, before,
                snapshot(position), actor.ipAddress());
        return view(position);
    }

    @Transactional
    public void delete(RequestActor actor, long id) {
        Position position = load(actor.companyId(), id);
        position.ensureDeletable(headcount(position).total());
        Map<String, Object> before = snapshot(position);
        try {
            positions.delete(position);
            positions.flush();
        } catch (DataIntegrityViolationException e) {
            // Còn bảng khác trỏ tới (lịch sử công tác, yêu cầu tuyển dụng...): giữ lại, đề nghị ngừng hoạt động
            throw new ApiException(HttpStatus.CONFLICT, "POSITION_IN_USE",
                    "Chức danh đang được dùng ở dữ liệu khác nên không xóa được. Hãy chọn Ngừng hoạt động.");
        }
        audit.record(actor.companyId(), actor.userId(), Action.DELETE, AuditEntity.POSITIONS, id, before, null,
                actor.ipAddress());
    }

    // ================================================================= nội bộ

    /** Khóa cấp bậc trước khi gắn chức danh vào, để không lọt vào cấp bậc đang bị ngừng hoạt động cùng lúc. */
    private JobGrade lockGrade(long companyId, Long gradeId) {
        if (gradeId == null) {
            throw gradeNotFound();
        }
        return grades.findForUpdate(gradeId, companyId).orElseThrow(PositionService::gradeNotFound);
    }

    private Position load(long companyId, long id) {
        return positions.findByIdAndCompanyId(id, companyId).orElseThrow(PositionService::notFound);
    }

    private Headcount headcount(Position position) {
        return headcounts.countByPosition(position.getCompanyId(), List.of(position.getId())).get(position.getId());
    }

    private PositionView view(Position position) {
        JobGrade grade = grades.findById(position.getJobGradeId()).orElseThrow();
        return toView(position, grade, headcount(position));
    }

    private static PositionView toView(Position p, JobGrade grade, Headcount count) {
        Headcount c = count == null ? Headcount.NONE : count;
        return new PositionView(p.getId(), p.getCode(), p.getName(), p.getJobGradeId(), grade.getCode(),
                grade.getName(), grade.isActive(), p.isActive(), c.total(), c.working(), p.getVersion(),
                p.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(Position p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", p.getCode());
        value.put("name", p.getName());
        value.put("jobGradeId", p.getJobGradeId());
        value.put("active", p.isActive());
        return value;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "Không tìm thấy chức danh.");
    }

    private static ApiException gradeNotFound() {
        return new ApiException(HttpStatus.BAD_REQUEST, "POSITION_GRADE_NOT_FOUND", "Không tìm thấy cấp bậc.");
    }

    private static ApiException codeExists(String code) {
        return new ApiException(HttpStatus.CONFLICT, "POSITION_CODE_EXISTS", "Mã chức danh " + code + " đã tồn tại.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
