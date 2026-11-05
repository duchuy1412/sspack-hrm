package vn.sspack.hrm.platform.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.sspack.hrm.platform.domain.auth.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Khóa dòng token để hai yêu cầu làm mới đồng thời không cùng xoay vòng một token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdateByHash(@Param("hash") String hash);

    /** Thu hồi mọi refresh token còn hiệu lực của một người dùng. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllActive(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
