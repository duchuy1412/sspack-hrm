package vn.sspack.hrm.org.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.sspack.hrm.org.domain.Position;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCompanyIdOrderByCodeAsc(Long companyId);

    Optional<Position> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);

    long countByJobGradeId(Long jobGradeId);

    long countByJobGradeIdAndActiveTrue(Long jobGradeId);
}
