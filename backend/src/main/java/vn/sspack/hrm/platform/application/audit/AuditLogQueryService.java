package vn.sspack.hrm.platform.application.audit;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository;
import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository.Filter;
import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository.Row;
import vn.sspack.hrm.shared.config.ClockConfig;
import vn.sspack.hrm.shared.error.ApiException;

/**
 * PLT-04: Admin xem nhật ký thao tác: ai, lúc nào, bảng nào, giá trị trước/sau. Khoảng ngày tính theo
 * giờ Việt Nam và gồm trọn ngày cuối.
 */
@Service
public class AuditLogQueryService {

    private final AuditLogJdbcRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogJdbcRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public record Criteria(LocalDate from, LocalDate to, Long userId, String entityType, Action action,
                           Long entityId) {

        public static Criteria none() {
            return new Criteria(null, null, null, null, null, null);
        }
    }

    public record AuditLogView(long id, OffsetDateTime createdAt, Long userId, String userEmail, String action,
                               String entityType, Long entityId, JsonNode oldValue, JsonNode newValue,
                               String ipAddress) {
    }

    public record ActorView(long id, String email) {
    }

    @Transactional(readOnly = true)
    public Page<AuditLogView> search(long companyId, Criteria criteria, Pageable pageable) {
        Filter filter = toFilter(companyId, criteria);
        long total = repository.count(filter);
        List<AuditLogView> content = total == 0 ? List.of()
                : repository.search(filter, (int) pageable.getOffset(), pageable.getPageSize()).stream()
                        .map(this::toView)
                        .toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<ActorView> actors(long companyId) {
        return repository.actors(companyId).stream().map(a -> new ActorView(a.userId(), a.email())).toList();
    }

    static Filter toFilter(long companyId, Criteria c) {
        if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_RANGE_INVALID",
                    "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
        if (c.entityType() != null && !AuditEntity.isKnownTable(c.entityType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_ENTITY_UNKNOWN",
                    "Bảng " + c.entityType() + " không có trong nhật ký.");
        }
        return new Filter(companyId,
                c.from() == null ? null : c.from().atStartOfDay(ClockConfig.ZONE).toOffsetDateTime(),
                c.to() == null ? null : c.to().plusDays(1).atStartOfDay(ClockConfig.ZONE).toOffsetDateTime(),
                c.userId(), c.entityType(), c.action() == null ? null : c.action().name(), c.entityId());
    }

    private AuditLogView toView(Row row) {
        return new AuditLogView(row.id(), row.createdAt().atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime(),
                row.userId(), row.userEmail(), row.action(), row.entityType(), row.entityId(),
                parse(row.oldValueJson()), parse(row.newValueJson()), row.ipAddress());
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Giá trị audit không phải JSON hợp lệ", e);
        }
    }
}
