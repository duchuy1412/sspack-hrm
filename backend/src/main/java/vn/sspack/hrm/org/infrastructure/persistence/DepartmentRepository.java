package vn.sspack.hrm.org.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.org.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdOrderByPathAsc(Long companyId);

    List<Department> findByCompanyIdAndActiveTrueOrderByPathAsc(Long companyId);

    Optional<Department> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Khóa dòng để hai thao tác chuyển cùng lúc không tạo vòng (A vào B và B vào A): người đến sau đọc lại
     * path mới nhất sau khi người trước commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id and d.companyId = :companyId")
    Optional<Department> findForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

    /** Id của phòng ban có path bắt đầu bằng {@code prefix}: chính nó và mọi cấp dưới. */
    @Query("select d.id from Department d where d.companyId = :companyId and d.path like concat(:prefix, '%')")
    List<Long> findIdsByPathPrefix(@Param("companyId") Long companyId, @Param("prefix") String prefix);

    List<Department> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);

    long countByParentId(Long parentId);

    long countByParentIdAndActiveTrue(Long parentId);

    /**
     * Đổi tiền tố path của mọi phòng ban con cháu sau khi chuyển một nhánh. Phòng ban được chuyển đã tự đổi
     * path qua entity nên bị loại ra.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE hrm.departments
            SET path = :newPrefix || substring(path FROM char_length(:oldPrefix) + 1),
                version = version + 1,
                updated_at = :now
            WHERE company_id = :companyId AND path LIKE :oldPrefix || '%' AND path <> :oldPrefix
            """, nativeQuery = true)
    int rewriteSubtreePaths(@Param("companyId") Long companyId, @Param("oldPrefix") String oldPrefix,
                            @Param("newPrefix") String newPrefix, @Param("now") OffsetDateTime now);
}
