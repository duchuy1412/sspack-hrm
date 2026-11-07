package vn.sspack.hrm.platform.application.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sspack.hrm.platform.application.audit.AuditEntity;
import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.domain.auth.LoginLockPolicy;
import vn.sspack.hrm.platform.domain.auth.RefreshToken;
import vn.sspack.hrm.platform.domain.auth.UserAccount;
import vn.sspack.hrm.platform.infrastructure.persistence.RefreshTokenRepository;
import vn.sspack.hrm.platform.infrastructure.persistence.UserAccountRepository;
import vn.sspack.hrm.shared.error.ApiException;

/**
 * PLT-01: đăng nhập bằng email/mật khẩu, làm mới và thu hồi phiên.
 *
 * <p>Mỗi thao tác chạy trong một transaction trả về <em>kết quả</em>; lỗi chỉ được ném sau khi transaction
 * đã commit. Nhờ vậy lần đăng nhập sai (tăng bộ đếm, khóa tài khoản, ghi audit) hay việc thu hồi mọi
 * phiên khi phát hiện token bị dùng lại vẫn được lưu dù API trả lỗi.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenCodec refreshTokenCodec;
    private final AuditLogService audit;
    private final AuthProperties properties;
    private final LoginLockPolicy lockPolicy;
    private final Clock clock;
    private final TransactionTemplate tx;
    /** Băm giả để so khớp khi email không tồn tại, giữ thời gian phản hồi như khi sai mật khẩu. */
    private final String dummyPasswordHash;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokenIssuer,
                       RefreshTokenCodec refreshTokenCodec, AuditLogService audit, AuthProperties properties,
                       Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenCodec = refreshTokenCodec;
        this.audit = audit;
        this.properties = properties;
        this.lockPolicy = properties.lockPolicy();
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.dummyPasswordHash = passwordEncoder.encode("sspack-hrm-timing-guard");
    }

    // ================================================================= đăng nhập

    public record LoginCommand(String email, String password, boolean rememberMe, String ipAddress) {

        @Override
        public String toString() {
            return "LoginCommand[email=" + email + ", rememberMe=" + rememberMe + "]";
        }
    }

    private sealed interface LoginOutcome {
        record Success(AuthTokens tokens) implements LoginOutcome {
        }

        record Failure(ApiException error) implements LoginOutcome {
        }
    }

    public AuthTokens login(LoginCommand command) {
        LoginOutcome outcome = tx.execute(status -> attemptLogin(command));
        return switch (outcome) {
            case LoginOutcome.Success s -> s.tokens();
            case LoginOutcome.Failure f -> throw f.error();
        };
    }

    private LoginOutcome attemptLogin(LoginCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<UserAccount> found = users.findForLogin(command.email().trim());

        if (found.isEmpty()) {
            passwordEncoder.matches(command.password(), dummyPasswordHash);
            log.info("Đăng nhập thất bại: email không tồn tại");
            return new LoginOutcome.Failure(AuthErrors.invalidCredentials());
        }

        UserAccount user = found.get();

        if (user.isTemporarilyLocked(now)) {
            auditLogin(user, "LOCKED", command.ipAddress());
            return new LoginOutcome.Failure(AuthErrors.locked(user.remainingLock(now).orElseThrow(),
                    user.getLockedUntil()));
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            boolean lockedNow = user.registerFailedLogin(now, lockPolicy);
            auditLogin(user, lockedNow ? "FAILED_AND_LOCKED" : "FAILED", command.ipAddress());
            return new LoginOutcome.Failure(lockedNow
                    ? AuthErrors.locked(lockPolicy.lockDuration(), user.getLockedUntil())
                    : AuthErrors.invalidCredentials());
        }

        if (!user.isEnabled()) {
            auditLogin(user, "DISABLED", command.ipAddress());
            return new LoginOutcome.Failure(AuthErrors.disabled());
        }

        user.registerSuccessfulLogin(now);
        AuthTokens tokens = issueTokens(user, command.rememberMe(), now);
        auditLogin(user, "SUCCESS", command.ipAddress());
        return new LoginOutcome.Success(tokens);
    }

    // ================================================================= làm mới

    public AuthTokens refresh(String rawRefreshToken, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthErrors.sessionExpired();
        }
        LoginOutcome outcome = tx.execute(status -> attemptRefresh(rawRefreshToken, ipAddress));
        return switch (outcome) {
            case LoginOutcome.Success s -> s.tokens();
            case LoginOutcome.Failure f -> throw f.error();
        };
    }

    private LoginOutcome attemptRefresh(String rawRefreshToken, String ipAddress) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<RefreshToken> found = refreshTokens.findForUpdateByHash(refreshTokenCodec.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return new LoginOutcome.Failure(AuthErrors.sessionExpired());
        }

        RefreshToken token = found.get();
        if (token.isRevoked()) {
            if (token.isReuseBeyondGrace(now, properties.refreshReuseGrace())) {
                handleReuse(token, now, ipAddress);
            }
            return new LoginOutcome.Failure(AuthErrors.sessionExpired());
        }
        if (token.isExpired(now)) {
            return new LoginOutcome.Failure(AuthErrors.sessionExpired());
        }

        Optional<UserAccount> user = users.findWithRolesById(token.getUserId());
        if (user.isEmpty() || !user.get().isEnabled()) {
            token.revoke(now);
            return new LoginOutcome.Failure(AuthErrors.sessionExpired());
        }

        token.revoke(now);
        return new LoginOutcome.Success(issueTokens(user.get(), token.isRememberMe(), now));
    }

    /** Token đã thu hồi bị dùng lại: coi như bị đánh cắp, thu hồi mọi phiên của người dùng. */
    private void handleReuse(RefreshToken token, OffsetDateTime now, String ipAddress) {
        int revoked = refreshTokens.revokeAllActive(token.getUserId(), now);
        log.warn("Phát hiện refresh token bị dùng lại cho user {}; đã thu hồi {} phiên", token.getUserId(), revoked);
        users.findById(token.getUserId()).ifPresent(u -> audit.record(u.getCompanyId(), u.getId(), Action.LOGIN,
                AuditEntity.REFRESH_TOKENS, token.getId(), null,
                Map.of("result", "REUSE_DETECTED", "revokedSessions", revoked), ipAddress));
    }

    // ================================================================= đăng xuất

    @Transactional
    public void logout(String rawRefreshToken, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Optional<RefreshToken> found = refreshTokens.findForUpdateByHash(refreshTokenCodec.hash(rawRefreshToken));
        if (found.isEmpty() || found.get().isRevoked()) {
            return;
        }
        RefreshToken token = found.get();
        token.revoke(OffsetDateTime.now(clock));
        Optional<UserAccount> user = users.findById(token.getUserId());
        if (user.isPresent()) {
            audit.record(user.get().getCompanyId(), user.get().getId(), Action.LOGOUT,
                    AuditEntity.REFRESH_TOKENS, token.getId(), null, Map.of("email", user.get().getEmail()),
                    ipAddress);
        }
    }

    // ================================================================= người dùng hiện tại

    @Transactional(readOnly = true)
    public CurrentUser currentUser(Long userId) {
        UserAccount user = users.findWithRolesById(userId).orElseThrow(AuthErrors::sessionExpired);
        var roles = user.getRoles().stream()
                .map(r -> new CurrentUser.RoleInfo(r.getCode(), r.getName(), r.getDataScope()))
                .sorted(Comparator.comparing(CurrentUser.RoleInfo::code))
                .toList();
        return new CurrentUser(user.getId(), user.getEmail(), user.getPreferredLocale(), roles,
                user.permissionCodes());
    }

    // ================================================================= nội bộ

    private AuthTokens issueTokens(UserAccount user, boolean rememberMe, OffsetDateTime now) {
        AccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(user, now);
        String rawRefresh = refreshTokenCodec.generate();
        RefreshToken refresh = refreshTokens.save(RefreshToken.issue(user.getId(),
                refreshTokenCodec.hash(rawRefresh), rememberMe, now, properties.refreshTokenTtl()));
        return new AuthTokens(access.value(), access.expiresAt(), rawRefresh, refresh.getExpiresAt(), rememberMe);
    }

    private void auditLogin(UserAccount user, String result, String ipAddress) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("result", result);
        value.put("email", user.getEmail());
        audit.record(user.getCompanyId(), user.getId(), Action.LOGIN, AuditEntity.USERS, user.getId(), null, value,
                ipAddress);
    }
}
