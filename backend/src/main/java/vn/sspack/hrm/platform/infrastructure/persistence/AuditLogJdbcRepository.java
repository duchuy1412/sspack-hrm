package vn.sspack.hrm.platform.infrastructure.persistence;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Ghi audit log bằng JDBC: bảng chỉ thêm dòng, có cột {@code inet} và {@code jsonb} nên ép kiểu trực tiếp
 * trong SQL thay vì qua entity.
 */
@Repository
public class AuditLogJdbcRepository {

    private final JdbcClient jdbc;

    public AuditLogJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long companyId, Long userId, String action, String entityType, Long entityId,
                       String oldValueJson, String newValueJson, String ipAddress, OffsetDateTime createdAt) {
        jdbc.sql("""
                        INSERT INTO hrm.audit_logs
                            (company_id, user_id, action, entity_type, entity_id, old_value, new_value, ip_address, created_at)
                        VALUES (:companyId, :userId, :action, :entityType, :entityId,
                                CAST(:oldValue AS jsonb), CAST(:newValue AS jsonb), CAST(:ip AS inet), :createdAt)
                        """)
                .param("companyId", companyId)
                .param("userId", userId)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("oldValue", oldValueJson)
                .param("newValue", newValueJson)
                .param("ip", ipAddress)
                .param("createdAt", createdAt)
                .update();
    }
}
