package vn.sspack.hrm.shared.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method ghi dữ liệu trong tầng {@code application} cố ý KHÔNG ghi audit log (PLT-04).
 * Test kiến trúc {@code AuditCoverageTest} yêu cầu mọi method {@code @Transactional} ghi dữ liệu phải gọi
 * {@code AuditLogService.record} hoặc mang chú thích này kèm lý do.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoAudit {

    /** Vì sao thao tác này không cần nhật ký. */
    String value();
}
