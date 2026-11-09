package vn.sspack.hrm.org.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * ORG-02: HR quản lý cấp bậc và khung lương. Mọi thao tác ghi đều ghi audit log với giá trị trước/sau.
 */
@Service
public class JobGradeService {

    private final JobGradeRepository grades;
    private final PositionRepository positions;
    private final AuditLogService audit;
    private final Clock clock;

    public JobGradeService(JobGradeRepository grades, PositionRepository positions, AuditLogService audit,
                           Clock clock) {
        this.grades = grades;
        this.positions = positions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @param positionCount       số chức danh thuộc cấp bậc (kể cả đã ngừng hoạt động)
     * @param activePositionCount số chức danh đang hoạt động
     */
    public record JobGradeView(Long id, String code, String name, BigDecimal minSalary, BigDecimal maxSalary,
                               boolean active, long positionCount, long activePositionCount, int version,
                               OffsetDateTime updatedAt) {
    }

    public record SaveCommand(String code, String name, BigDecimal minSalary, BigDecimal maxSalary) {
    }

    public record UpdateCommand(String code, String name, BigDecimal minSalary, BigDecimal maxSalary,
                                int version) {
    }

    public record StatusCommand(boolean active, int version) {
    }

    // ================================================================= đọc

    /** Mọi cấp bậc theo khung lương tăng dần; đã ngừng hoạt động chỉ có khi {@code includeInactive}. */
    @Transactional(readOnly = true)
    public List<JobGradeView> list(long companyId, boolean includeInactive) {
        Map<Long, long[]> counts = new HashMap<>();
        for (Position p : positions.findByCompanyIdOrderByCodeAsc(companyId)) {
            long[] c = counts.computeIfAbsent(p.getJobGradeId(), k -> new long[2]);
            c[0]++;
            if (p.isActive()) {
                c[1]++;
            }
        }
        return grades.findByCompanyIdOrderByMinSalaryAscMaxSalaryAscCodeAsc(companyId).stream()
                .filter(g -> includeInactive || g.isActive())
                .map(g -> {
                    long[] c = counts.getOrDefault(g.getId(), new long[2]);
                    return toView(g, c[0], c[1]);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public JobGradeView get(long companyId, long id) {
        return view(load(companyId, id));
    }

    // ================================================================= ghi

    @Transactional
    public JobGradeView create(RequestActor actor, SaveCommand command) {
        JobGrade grade = JobGrade.create(actor.companyId(), command.code(), command.name(), command.minSalary(),
                command.maxSalary(), now());
        if (grades.existsByCompanyIdAndCode(actor.companyId(), grade.getCode())) {
            throw codeExists(grade.getCode());
        }
        grade = grades.saveAndFlush(grade);
        audit.record(actor.companyId(), actor.userId(), Action.CREATE, AuditEntity.JOB_GRADES, grade.getId(), null,
                snapshot(grade), actor.ipAddress());
        return view(grade);
    }

    @Transactional
    public JobGradeView update(RequestActor actor, long id, UpdateCommand command) {
        JobGrade grade = load(actor.companyId(), id);
        checkVersion(grade.getVersion(), command.version());
        Map<String, Object> before = snapshot(grade);
        grade.update(command.code(), command.name(), command.minSalary(), command.maxSalary(), now());
        if (grades.existsByCompanyIdAndCodeAndIdNot(actor.companyId(), grade.getCode(), id)) {
            throw codeExists(grade.getCode());
        }
        grades.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.JOB_GRADES, id, before,
                snapshot(grade), actor.ipAddress());
        return view(grade);
    }

    @Transactional
    public JobGradeView changeStatus(RequestActor actor, long id, StatusCommand command) {
        JobGrade grade = grades.findForUpdate(id, actor.companyId()).orElseThrow(JobGradeService::notFound);
        checkVersion(grade.getVersion(), command.version());
        if (grade.isActive() == command.active()) {
            return view(grade);
        }
        Map<String, Object> before = snapshot(grade);
        if (command.active()) {
            grade.activate(now());
        } else {
            grade.deactivate(positions.countByJobGradeIdAndActiveTrue(id), now());
        }
        grades.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, AuditEntity.JOB_GRADES, id, before,
                snapshot(grade), actor.ipAddress());
        return view(grade);
    }

    @Transactional
    public void delete(RequestActor actor, long id) {
        JobGrade grade = grades.findForUpdate(id, actor.companyId()).orElseThrow(JobGradeService::notFound);
        grade.ensureDeletable(positions.countByJobGradeId(id));
        Map<String, Object> before = snapshot(grade);
        try {
            grades.delete(grade);
            grades.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_GRADE_IN_USE",
                    "Cấp bậc đang được dùng ở dữ liệu khác nên không xóa được. Hãy chọn Ngừng hoạt động.");
        }
        audit.record(actor.companyId(), actor.userId(), Action.DELETE, AuditEntity.JOB_GRADES, id, before, null,
                actor.ipAddress());
    }

    // ================================================================= nội bộ

    private JobGrade load(long companyId, long id) {
        return grades.findByIdAndCompanyId(id, companyId).orElseThrow(JobGradeService::notFound);
    }

    private JobGradeView view(JobGrade grade) {
        return toView(grade, positions.countByJobGradeId(grade.getId()),
                positions.countByJobGradeIdAndActiveTrue(grade.getId()));
    }

    private static JobGradeView toView(JobGrade g, long positionCount, long activePositionCount) {
        return new JobGradeView(g.getId(), g.getCode(), g.getName(), g.getMinSalary(), g.getMaxSalary(),
                g.isActive(), positionCount, activePositionCount, g.getVersion(), g.getUpdatedAt());
    }

    static void checkVersion(int current, int expected) {
        if (current != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại và thử lại.");
        }
    }

    private static Map<String, Object> snapshot(JobGrade g) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", g.getCode());
        value.put("name", g.getName());
        value.put("minSalary", g.getMinSalary());
        value.put("maxSalary", g.getMaxSalary());
        value.put("active", g.isActive());
        return value;
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "JOB_GRADE_NOT_FOUND", "Không tìm thấy cấp bậc.");
    }

    private static ApiException codeExists(String code) {
        return new ApiException(HttpStatus.CONFLICT, "JOB_GRADE_CODE_EXISTS", "Mã cấp bậc " + code + " đã tồn tại.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
