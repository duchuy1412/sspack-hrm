package vn.sspack.hrm.platform.infrastructure.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.platform.domain.auth.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Tìm tài khoản theo email (không phân biệt hoa thường) và khóa dòng để các lần đăng nhập đồng thời
     * cập nhật bộ đếm sai tuần tự. MVP single-tenant nên email là duy nhất trong hệ thống.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findForLogin(@Param("email") String email);

    /** Danh sách tài khoản của công ty, lọc theo email chứa {@code q} (không phân biệt hoa thường). */
    @Query("""
            select u from UserAccount u
            where u.companyId = :companyId
              and (:q = '' or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    Page<UserAccount> search(@Param("companyId") Long companyId, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesByIdAndCompanyId(Long id, Long companyId);

    /** Nạp tài khoản kèm vai trò và quyền để phát hành token. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findWithRolesById(@Param("id") Long id);
}
