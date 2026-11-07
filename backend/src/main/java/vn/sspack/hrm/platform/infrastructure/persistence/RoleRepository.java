package vn.sspack.hrm.platform.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.platform.domain.auth.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Page<Role> findByCompanyId(Long companyId, Pageable pageable);

    List<Role> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    @Query(value = "SELECT count(*) FROM user_roles WHERE role_id = :roleId", nativeQuery = true)
    long countAssignedUsers(@Param("roleId") Long roleId);

    @Query(value = "SELECT role_id, count(*) FROM user_roles WHERE role_id IN (:roleIds) GROUP BY role_id",
            nativeQuery = true)
    List<Object[]> countAssignedUsersRaw(@Param("roleIds") Collection<Long> roleIds);

    /** Số người dùng theo vai trò; vai trò không có ai thì không nằm trong map. */
    default Map<Long, Long> countAssignedUsers(Collection<Long> roleIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (roleIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : countAssignedUsersRaw(roleIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
