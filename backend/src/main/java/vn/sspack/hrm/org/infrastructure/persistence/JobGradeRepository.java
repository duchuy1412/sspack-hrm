package vn.sspack.hrm.org.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.org.domain.JobGrade;

public interface JobGradeRepository extends JpaRepository<JobGrade, Long> {

    /** Thứ tự hiển thị: khung lương từ thấp đến cao. */
    List<JobGrade> findByCompanyIdOrderByMinSalaryAscMaxSalaryAscCodeAsc(Long companyId);

    Optional<JobGrade> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Khóa dòng cấp bậc khi gắn chức danh vào nó hoặc khi ngừng hoạt động/xóa nó, để không có chức danh
     * mới lọt vào một cấp bậc vừa bị ngừng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from JobGrade g where g.id = :id and g.companyId = :companyId")
    Optional<JobGrade> findForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);
}
