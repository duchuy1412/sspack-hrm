package vn.sspack.hrm.platform.application.rbac;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.application.rbac.RbacViews.Actor;
import vn.sspack.hrm.platform.application.rbac.RbacViews.AssignRolesCommand;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleRef;
import vn.sspack.hrm.platform.application.rbac.RbacViews.UserRolesView;
import vn.sspack.hrm.platform.domain.auth.Role;
import vn.sspack.hrm.platform.domain.auth.UserAccount;
import vn.sspack.hrm.platform.infrastructure.persistence.RoleRepository;
import vn.sspack.hrm.platform.infrastructure.persistence.UserAccountRepository;

/**
 * PLT-03: gán vai trò cho tài khoản. Màn quản lý tài khoản đầy đủ (tạo, khóa, đặt lại mật khẩu) không thuộc
 * phạm vi này; ở đây chỉ liệt kê tài khoản và thay tập vai trò của một tài khoản.
 */
@Service
public class UserRoleService {

    static final String ENTITY_USER = "user";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final AuditLogService audit;
    private final Clock clock;

    public UserRoleService(UserAccountRepository users, RoleRepository roles, AuditLogService audit, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserRolesView> listUsers(long companyId, String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        return users.search(companyId, q,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("email")))
                .map(UserRoleService::toView);
    }

    @Transactional
    public UserRolesView assignRoles(Actor actor, long userId, AssignRolesCommand command) {
        UserAccount user = users.findWithRolesByIdAndCompanyId(userId, actor.companyId())
                .orElseThrow(RbacErrors::userNotFound);
        if (user.getVersion() != command.version()) {
            throw RbacErrors.staleVersion();
        }
        Set<Long> roleIds = command.roleIds() == null ? Set.of() : command.roleIds();
        List<Role> newRoles = roleIds.isEmpty() ? List.of() : roles.findByCompanyIdAndIdIn(actor.companyId(), roleIds);
        if (newRoles.size() != roleIds.size()) {
            Set<Long> unknown = new HashSet<>(roleIds);
            newRoles.forEach(r -> unknown.remove(r.getId()));
            throw RbacErrors.unknownRoles(unknown);
        }
        Map<String, Object> before = snapshot(user);
        user.assignRoles(newRoles, user.getId() == actor.userId(), OffsetDateTime.now(clock));
        users.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, ENTITY_USER, user.getId(), before,
                snapshot(user), actor.ipAddress());
        return toView(user);
    }

    private static UserRolesView toView(UserAccount user) {
        List<RoleRef> roleRefs = user.getRoles().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(r -> new RoleRef(r.getId(), r.getCode(), r.getName()))
                .toList();
        return new UserRolesView(user.getId(), user.getEmail(), user.getStatus().name(), roleRefs, user.getVersion());
    }

    private static Map<String, Object> snapshot(UserAccount user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("email", user.getEmail());
        value.put("roles", List.copyOf(user.roleCodes()));
        return value;
    }
}
