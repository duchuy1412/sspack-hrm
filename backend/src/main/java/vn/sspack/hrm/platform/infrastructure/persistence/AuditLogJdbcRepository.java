package vn.sspack.hrm.platform.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Nhật ký thao tác bằng JDBC: bảng chỉ thêm dòng (trigger chặn sửa/xóa), có cột {@code inet} và
 * {@code jsonb} nên ép kiểu trực tiếp trong SQL thay vì qua entity.
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

    /** Điều kiện lọc; trường null = không lọc. Khoảng thời gian là nửa mở {@code [from, toExclusive)}. */
    public record Filter(long companyId, OffsetDateTime from, OffsetDateTime toExclusive, Long userId,
                         String entityType, String action, Long entityId) {
    }

    public record Row(long id, OffsetDateTime createdAt, Long userId, String userEmail, String action,
                      String entityType, Long entityId, String oldValueJson, String newValueJson,
                      String ipAddress) {
    }

    public record Actor(long userId, String email) {
    }

    /** Một trang nhật ký, mới nhất trước (id giảm dần khi trùng thời điểm). */
    public List<Row> search(Filter filter, int offset, int limit) {
        Where where = where(filter);
        return where.bind(jdbc.sql("""
                        SELECT a.id, a.created_at, a.user_id, u.email, a.action, a.entity_type, a.entity_id,
                               a.old_value::text AS old_value, a.new_value::text AS new_value,
                               host(a.ip_address) AS ip_address
                        FROM hrm.audit_logs a
                        LEFT JOIN hrm.users u ON u.id = a.user_id
                        """ + where.sql + """
                        ORDER BY a.created_at DESC, a.id DESC
                        LIMIT :limit OFFSET :offset
                        """))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> new Row(
                        rs.getLong("id"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        (Long) rs.getObject("user_id"),
                        rs.getString("email"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        (Long) rs.getObject("entity_id"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getString("ip_address")))
                .list();
    }

    public long count(Filter filter) {
        Where where = where(filter);
        return where.bind(jdbc.sql("SELECT count(*) FROM hrm.audit_logs a " + where.sql))
                .query(Long.class)
                .single();
    }

    /** Tài khoản từng có dòng nhật ký, theo email; dùng cho ô lọc "người thực hiện". */
    public List<Actor> actors(long companyId) {
        return jdbc.sql("""
                        SELECT u.id, u.email
                        FROM hrm.users u
                        WHERE u.company_id = :companyId
                          AND EXISTS (SELECT 1 FROM hrm.audit_logs a WHERE a.user_id = u.id AND a.company_id = :companyId)
                        ORDER BY u.email
                        """)
                .param("companyId", companyId)
                .query((rs, n) -> new Actor(rs.getLong("id"), rs.getString("email")))
                .list();
    }

    private record Where(String sql, Map<String, Object> params) {
        JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec) {
            params.forEach(spec::param);
            return spec;
        }
    }

    private static Where where(Filter filter) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        clauses.add("a.company_id = :companyId");
        params.put("companyId", filter.companyId());
        if (filter.from() != null) {
            clauses.add("a.created_at >= :from");
            params.put("from", filter.from());
        }
        if (filter.toExclusive() != null) {
            clauses.add("a.created_at < :toExclusive");
            params.put("toExclusive", filter.toExclusive());
        }
        if (filter.userId() != null) {
            clauses.add("a.user_id = :userId");
            params.put("userId", filter.userId());
        }
        if (filter.entityType() != null) {
            clauses.add("a.entity_type = :entityType");
            params.put("entityType", filter.entityType());
        }
        if (filter.action() != null) {
            clauses.add("a.action = :action");
            params.put("action", filter.action());
        }
        if (filter.entityId() != null) {
            clauses.add("a.entity_id = :entityId");
            params.put("entityId", filter.entityId());
        }
        return new Where("WHERE " + String.join(" AND ", clauses) + "\n", params);
    }
}
