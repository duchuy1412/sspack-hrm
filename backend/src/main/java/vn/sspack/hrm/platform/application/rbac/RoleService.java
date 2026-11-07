package vn.sspack.hrm.platform.application.rbac;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.application.rbac.RbacViews.Actor;
import vn.sspack.hrm.platform.application.rbac.RbacViews.CreateRoleCommand;
import vn.sspack.hrm.platform.application.rbac.RbacViews.PermissionView;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleDetail;
import vn.sspack.hrm.platform.application.rbac.RbacViews.RoleSummary;
import vn.sspack.hrm.platform.application.rbac.RbacViews.UpdateRoleCommand;
import vn.sspack.hrm.platform.domain.auth.Permission;
import vn.sspack.hrm.platform.domain.auth.Role;
import vn.sspack.hrm.platform.infrastructure.persistence.PermissionRepository;
import vn.sspack.hrm.platform.infrastructure.persistence.RoleRepository;

/**
 * PLT-03: Admin tạo vai trò và gán quyền theo chức năng. Mọi thao tác ghi đều ghi audit log với giá trị
 * trước/sau. Quyền mới có hiệu lực với người đang đăng nhập khi access token được làm mới (tối đa 15 phút).
 */
@Service
public class RoleService {

    static final String ENTITY_ROLE = "role";
    /** Vai trò hệ thống lên trước, sau đó theo mã. */
    static final Sort ROLE_ORDER = Sort.by(Sort.Order.desc("system"), Sort.Order.asc("code"));

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AuditLogService audit;
    private final Clock clock;

    public RoleService(RoleRepository roles, PermissionRepository permissions, AuditLogService audit, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream()
                .map(p -> new PermissionView(p.getCode(), p.getModule(), p.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RoleSummary> listRoles(long companyId, Pageable pageable) {
        Page<Role> page = roles.findByCompanyId(companyId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ROLE_ORDER));
        Map<Long, Long> userCounts = roles.countAssignedUsers(page.map(Role::getId).getContent());
        return page.map(r -> new RoleSummary(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getDataScope(), r.isSystem(), r.getPermissions().size(), userCounts.getOrDefault(r.getId(), 0L),
                r.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public RoleDetail getRole(long companyId, long roleId) {
        Role role = load(companyId, roleId);
        return toDetail(role, roles.countAssignedUsers(role.getId()));
    }

    @Transactional
    public RoleDetail create(Actor actor, CreateRoleCommand command) {
        List<Permission> granted = resolvePermissions(command.permissions());
        Role role = Role.create(actor.companyId(), command.code(), command.name(), command.description(),
                command.dataScope(), granted, now());
        if (roles.existsByCompanyIdAndCode(actor.companyId(), role.getCode())) {
            throw RbacErrors.roleCodeExists(role.getCode());
        }
        role = roles.saveAndFlush(role);
        audit.record(actor.companyId(), actor.userId(), Action.CREATE, ENTITY_ROLE, role.getId(), null,
                snapshot(role), actor.ipAddress());
        return toDetail(role, 0);
    }

    @Transactional
    public RoleDetail update(Actor actor, long roleId, UpdateRoleCommand command) {
        Role role = load(actor.companyId(), roleId);
        if (role.getVersion() != command.version()) {
            throw RbacErrors.staleVersion();
        }
        Map<String, Object> before = snapshot(role);
        role.update(command.name(), command.description(), command.dataScope(),
                resolvePermissions(command.permissions()), now());
        roles.flush();
        audit.record(actor.companyId(), actor.userId(), Action.UPDATE, ENTITY_ROLE, role.getId(), before,
                snapshot(role), actor.ipAddress());
        return toDetail(role, roles.countAssignedUsers(role.getId()));
    }

    @Transactional
    public void delete(Actor actor, long roleId) {
        Role role = load(actor.companyId(), roleId);
        role.ensureDeletable(roles.countAssignedUsers(role.getId()));
        Map<String, Object> before = snapshot(role);
        roles.delete(role);
        audit.record(actor.companyId(), actor.userId(), Action.DELETE, ENTITY_ROLE, roleId, before, null,
                actor.ipAddress());
    }

    // ================================================================= nội bộ

    private Role load(long companyId, long roleId) {
        return roles.findWithPermissionsByIdAndCompanyId(roleId, companyId).orElseThrow(RbacErrors::roleNotFound);
    }

    private List<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> unknown = new TreeSet<>(codes);
            found.forEach(p -> unknown.remove(p.getCode()));
            throw RbacErrors.unknownPermissions(unknown);
        }
        return found;
    }

    private RoleDetail toDetail(Role role, long assignedUserCount) {
        return new RoleDetail(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getDataScope(), role.isSystem(), role.permissionCodes(), assignedUserCount, role.getVersion(),
                role.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(Role role) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", role.getCode());
        value.put("name", role.getName());
        value.put("description", role.getDescription());
        value.put("dataScope", role.getDataScope().name());
        value.put("permissions", new ArrayList<>(role.permissionCodes()));
        return value;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
