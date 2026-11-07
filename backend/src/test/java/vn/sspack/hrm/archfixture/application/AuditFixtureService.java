package vn.sspack.hrm.archfixture.application;

import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.application.audit.AuditEntity;
import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.shared.audit.NoAudit;

/** Mẫu cho {@code AuditCoverageTest}: chỉ {@link #forgotAudit()} vi phạm quy tắc. */
@SuppressWarnings("unused")
public class AuditFixtureService {

    private final AuditLogService audit = null;

    @Transactional
    public void recordsDirectly() {
        audit.record(1, 1L, Action.CREATE, AuditEntity.ROLES, 1L, null, null, null);
    }

    @Transactional
    public void recordsViaHelper() {
        helper();
    }

    @Transactional
    public void recordsInLambda() {
        java.util.Optional.of(1).ifPresent(x -> helper());
    }

    @Transactional
    @NoAudit("chỉ dọn bộ nhớ đệm")
    public void explained() {
    }

    @Transactional(readOnly = true)
    public void readOnly() {
    }

    @Transactional
    public void forgotAudit() {
    }

    private void helper() {
        audit.record(1, 1L, Action.UPDATE, AuditEntity.ROLES, 1L, null, null, null);
    }
}
