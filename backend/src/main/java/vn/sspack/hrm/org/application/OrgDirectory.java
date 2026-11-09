package vn.sspack.hrm.org.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.org.domain.Department;
import vn.sspack.hrm.org.domain.Position;
import vn.sspack.hrm.org.infrastructure.persistence.DepartmentRepository;
import vn.sspack.hrm.org.infrastructure.persistence.PositionRepository;
import vn.sspack.hrm.shared.audit.NoAudit;

/**
 * Điểm vào công khai của module org cho module khác tra phòng ban, chức danh (EMP-01 gán nhân viên vào phòng ban
 * và chức danh; lọc theo phạm vi dữ liệu cần cây phòng ban). Chỉ đọc, trả về bản tóm tắt chứ không trả entity.
 */
@Service
@Transactional(readOnly = true)
public class OrgDirectory {

    public record DepartmentRef(Long id, String code, String name, boolean active) {
    }

    public record PositionRef(Long id, String code, String name, boolean active) {
    }

    private final DepartmentRepository departments;
    private final PositionRepository positions;

    public OrgDirectory(DepartmentRepository departments, PositionRepository positions) {
        this.departments = departments;
        this.positions = positions;
    }

    public Map<Long, DepartmentRef> departments(long companyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departments.findByCompanyIdAndIdIn(companyId, ids).stream()
                .map(OrgDirectory::ref)
                .collect(Collectors.toMap(DepartmentRef::id, Function.identity()));
    }

    public Map<Long, PositionRef> positions(long companyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return positions.findByCompanyIdAndIdIn(companyId, ids).stream()
                .map(OrgDirectory::ref)
                .collect(Collectors.toMap(PositionRef::id, Function.identity()));
    }

    public Optional<PositionRef> position(long companyId, long id) {
        return positions.findByIdAndCompanyId(id, companyId).map(OrgDirectory::ref);
    }

    /** Id của phòng ban và mọi phòng ban cấp dưới (kể cả đã ngừng hoạt động); rỗng nếu không có phòng ban. */
    public List<Long> subtreeIds(long companyId, long departmentId) {
        return departments.findByIdAndCompanyId(departmentId, companyId)
                .map(d -> departments.findIdsByPathPrefix(companyId, d.getPath()))
                .orElse(List.of());
    }

    /**
     * Đọc và khóa dòng phòng ban trong transaction của người gọi (bắt buộc có sẵn), để phòng ban không bị ngừng
     * hoạt động giữa lúc kiểm tra và lúc gán nhân viên vào.
     */
    @NoAudit("Chỉ đọc và khóa dòng phòng ban; người gọi tự ghi audit cho thao tác của mình")
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DepartmentRef> lockDepartment(long companyId, long id) {
        return departments.findForUpdate(id, companyId).map(OrgDirectory::ref);
    }

    private static DepartmentRef ref(Department d) {
        return new DepartmentRef(d.getId(), d.getCode(), d.getName(), d.isActive());
    }

    private static PositionRef ref(Position p) {
        return new PositionRef(p.getId(), p.getCode(), p.getName(), p.isActive());
    }
}
