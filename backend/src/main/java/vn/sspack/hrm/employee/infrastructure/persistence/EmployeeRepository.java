package vn.sspack.hrm.employee.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.employee.domain.Employee;
import vn.sspack.hrm.employee.domain.EmployeeStatus;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Employee> findByCompanyIdAndUserId(Long companyId, Long userId);

    List<Employee> findByCompanyIdAndStatusIn(Long companyId, Collection<EmployeeStatus> statuses);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);

    /** Email công việc đã được lưu chữ thường nên so sánh bằng. */
    boolean existsByCompanyIdAndWorkEmail(Long companyId, String workEmail);

    boolean existsByCompanyIdAndWorkEmailAndIdNot(Long companyId, String workEmail, Long id);

    /** Số lớn nhất trong các mã dạng {@code <prefix><chữ số>}, 0 nếu chưa có. */
    @Query(value = """
            SELECT coalesce(max(substring(employee_code FROM char_length(:prefix) + 1)::bigint), 0)
            FROM hrm.employees
            WHERE company_id = :companyId AND employee_code ~ ('^' || :prefix || '[0-9]{1,15}$')
            """, nativeQuery = true)
    long maxNumericCode(@Param("companyId") Long companyId, @Param("prefix") String prefix);
}
