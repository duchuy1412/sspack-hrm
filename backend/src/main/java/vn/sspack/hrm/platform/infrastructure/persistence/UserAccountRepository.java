package vn.sspack.hrm.platform.infrastructure.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
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

    /** Nạp tài khoản kèm vai trò và quyền để phát hành token. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findWithRolesById(@Param("id") Long id);
}
