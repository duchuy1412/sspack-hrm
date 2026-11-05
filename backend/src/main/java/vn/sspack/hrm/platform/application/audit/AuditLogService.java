package vn.sspack.hrm.platform.application.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository;

/**
 * Ghi nhật ký thao tác (bảng {@code audit_logs}). Chạy trong transaction của nghiệp vụ gọi nó để nhật ký
 * và dữ liệu cùng commit hoặc cùng rollback. Màn xem nhật ký là PLT-04.
 */
@Service
public class AuditLogService {

    public enum Action { CREATE, UPDATE, DELETE, LOGIN, EXPORT }

    private final AuditLogJdbcRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogService(AuditLogJdbcRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long companyId, Long userId, Action action, String entityType, Long entityId,
                       Map<String, ?> oldValue, Map<String, ?> newValue, String ipAddress) {
        repository.insert(companyId, userId, action.name(), entityType, entityId,
                toJson(oldValue), toJson(newValue), ipAddress, OffsetDateTime.now(clock));
    }

    private String toJson(Map<String, ?> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Không chuyển được giá trị audit sang JSON", e);
        }
    }
}
