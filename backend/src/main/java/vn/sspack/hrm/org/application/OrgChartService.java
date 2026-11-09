package vn.sspack.hrm.org.application;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.employee.application.EmployeeDirectoryService;
import vn.sspack.hrm.employee.application.EmployeeDirectoryService.DirectoryCard;
import vn.sspack.hrm.org.domain.Department;
import vn.sspack.hrm.org.domain.JobGrade;
import vn.sspack.hrm.org.domain.Position;
import vn.sspack.hrm.org.infrastructure.persistence.DepartmentRepository;
import vn.sspack.hrm.org.infrastructure.persistence.JobGradeRepository;
import vn.sspack.hrm.org.infrastructure.persistence.PositionRepository;

/**
 * ORG-03: sơ đồ tổ chức. Cây phòng ban đang hoạt động, mỗi phòng ban kèm nhân viên đang làm việc với thông tin
 * công việc (tên, chức danh, email công việc). Mọi người có {@code org:read} xem được cả công ty, như danh bạ;
 * hồ sơ đầy đủ vẫn lọc theo phạm vi dữ liệu ở module employee.
 */
@Service
public class OrgChartService {

    /** Một người trên sơ đồ. {@code avatarUrl} luôn null cho đến khi có ảnh hồ sơ (EMP-07). */
    public record Member(Long id, String code, String fullName, String positionName, String workEmail,
                         String avatarUrl, boolean profileVisible, boolean self) {
    }

    /** Phòng ban trên sơ đồ, trả phẳng theo thứ tự cây; frontend dựng cây từ {@code parentId}. */
    public record ChartDepartment(Long id, Long parentId, String code, String name, int depth,
                                  List<Member> members) {
    }

    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final JobGradeRepository grades;
    private final EmployeeDirectoryService directory;

    public OrgChartService(DepartmentRepository departments, PositionRepository positions,
                           JobGradeRepository grades, EmployeeDirectoryService directory) {
        this.departments = departments;
        this.positions = positions;
        this.grades = grades;
        this.directory = directory;
    }

    /**
     * Sơ đồ của công ty. Trong mỗi phòng ban, người có cấp bậc cao hơn đứng trước (trưởng phòng trước nhân
     * viên), cùng cấp thì theo tên tiếng Việt. Khung lương không lộ ra, chỉ dùng thứ tự cấp bậc.
     */
    @Transactional(readOnly = true)
    public List<ChartDepartment> chart(long companyId, long viewerUserId) {
        List<Department> active = departments.findByCompanyIdAndActiveTrueOrderByPathAsc(companyId);
        Map<Long, Position> positionById = positions.findByCompanyIdOrderByCodeAsc(companyId).stream()
                .collect(Collectors.toMap(Position::getId, Function.identity()));
        List<JobGrade> gradeOrder = grades.findByCompanyIdOrderByMinSalaryAscMaxSalaryAscCodeAsc(companyId);
        Map<Long, Integer> gradeRank = new HashMap<>();
        for (int i = 0; i < gradeOrder.size(); i++) {
            gradeRank.put(gradeOrder.get(i).getId(), i);
        }

        Map<Long, List<DirectoryCard>> byDepartment = directory.workingEmployees(companyId, viewerUserId).stream()
                .collect(Collectors.groupingBy(DirectoryCard::departmentId));
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
        Comparator<DirectoryCard> order = Comparator
                .comparingInt((DirectoryCard c) -> -rank(c, positionById, gradeRank))
                .thenComparing(DirectoryCard::fullName, vietnamese);

        List<ChartDepartment> result = new ArrayList<>();
        for (Department d : active) {
            List<Member> members = byDepartment.getOrDefault(d.getId(), List.of()).stream()
                    .sorted(order)
                    .map(c -> member(c, positionById.get(c.positionId())))
                    .toList();
            result.add(new ChartDepartment(d.getId(), d.getParentId(), d.getCode(), d.getName(), d.depth(),
                    members));
        }
        return result;
    }

    private static int rank(DirectoryCard card, Map<Long, Position> positions, Map<Long, Integer> gradeRank) {
        Position p = card.positionId() == null ? null : positions.get(card.positionId());
        return p == null ? -1 : gradeRank.getOrDefault(p.getJobGradeId(), -1);
    }

    private static Member member(DirectoryCard c, Position position) {
        return new Member(c.id(), c.code(), c.fullName(), position == null ? null : position.getName(),
                c.workEmail(), null, c.profileVisible(), c.self());
    }
}
