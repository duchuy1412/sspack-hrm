package vn.sspack.hrm.platform.domain.auth;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Refresh token (PLT-01). Chỉ lưu bản băm SHA-256 của chuỗi ngẫu nhiên; chuỗi gốc chỉ nằm trong cookie
 * HttpOnly của trình duyệt. Mỗi lần làm mới, token cũ bị thu hồi và cấp token mới (xoay vòng).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 100)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    protected RefreshToken() {
    }

    public static RefreshToken issue(Long userId, String tokenHash, boolean rememberMe,
                                     OffsetDateTime now, Duration ttl) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.rememberMe = rememberMe;
        token.createdAt = now;
        token.expiresAt = now.plus(ttl);
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(OffsetDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /**
     * Token đã thu hồi bị dùng lại sau khoảng ân hạn: dấu hiệu token bị đánh cắp. Trong khoảng ân hạn
     * (vd hai tab cùng làm mới một lúc) chỉ từ chối, không coi là tấn công.
     */
    public boolean isReuseBeyondGrace(OffsetDateTime now, Duration grace) {
        return revokedAt != null && revokedAt.plus(grace).isBefore(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }
}
