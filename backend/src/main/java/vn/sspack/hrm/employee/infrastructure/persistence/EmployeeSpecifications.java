package vn.sspack.hrm.employee.infrastructure.persistence;

import java.util.Collection;
import java.util.Locale;

import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import vn.sspack.hrm.employee.domain.Employee;
import vn.sspack.hrm.employee.domain.EmployeeStatus;

/** Điều kiện lọc danh sách nhân viên (EMP-01, EMP-05). */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> inCompany(long companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<Employee> inDepartments(Collection<Long> departmentIds) {
        return (root, query, cb) -> departmentIds.isEmpty() ? cb.disjunction()
                : root.get("departmentId").in(departmentIds);
    }

    public static Specification<Employee> hasId(long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Employee> hasStatus(Collection<EmployeeStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /**
     * Tìm theo họ tên, mã hoặc email, không phân biệt hoa thường và dấu tiếng Việt ("nguyen van" khớp
     * "Nguyễn Văn") nhờ {@code unaccent} của PostgreSQL (migration V7).
     */
    public static Specification<Employee> matches(String q) {
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Expression<String> needle = cb.function("unaccent", String.class, cb.literal(pattern));
            return cb.or(
                    cb.like(cb.function("unaccent", String.class, cb.lower(root.get("fullName"))), needle, '\\'),
                    cb.like(cb.lower(root.get("code")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("workEmail"), "")), pattern, '\\'));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
