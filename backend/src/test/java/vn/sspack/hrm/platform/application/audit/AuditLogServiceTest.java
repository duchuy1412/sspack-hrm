package vn.sspack.hrm.platform.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.sspack.hrm.platform.application.audit.AuditLogQueryService.Criteria;
import vn.sspack.hrm.platform.application.audit.AuditLogService.Action;
import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository;
import vn.sspack.hrm.platform.infrastructure.persistence.AuditLogJdbcRepository.Filter;
import vn.sspack.hrm.shared.error.ApiException;

/** PLT-04: không ghi bí mật vào nhật ký; quy đổi bộ lọc ngày theo giờ Việt Nam. */
class AuditLogServiceTest {

    final AuditLogJdbcRepository repository = mock(AuditLogJdbcRepository.class);
    final AuditLogService service = new AuditLogService(repository, new ObjectMapper(), Clock.systemUTC());

    @Test
    @DisplayName("Snapshot có khóa mật khẩu hoặc token: từ chối, không ghi gì")
    void rejectsSecrets() {
        for (String key : new String[] {"password", "passwordHash", "PASSWORD_HASH", "token", "tokenHash", "secret"}) {
            assertThatThrownBy(() -> service.record(1, 1L, Action.UPDATE, AuditEntity.USERS, 1L,
                    null, Map.of(key, "x"), null))
                    .as(key)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);
        }
        verify(repository, never()).insert(any(Long.class), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ghi tên bảng thật làm entity_type")
    void writesTableName() {
        service.record(1, 2L, Action.DELETE, AuditEntity.ROLES, 9L, Map.of("code", "X"), null, "10.0.0.1");

        verify(repository).insert(any(Long.class), any(), any(), org.mockito.ArgumentMatchers.eq("roles"),
                org.mockito.ArgumentMatchers.eq(9L), any(), org.mockito.ArgumentMatchers.isNull(), any(), any());
    }

    @Test
    @DisplayName("Khoảng ngày: từ 00:00 ngày đầu tới trước 00:00 ngày sau ngày cuối, giờ +07:00")
    void dateRangeInVietnamTime() {
        Filter filter = AuditLogQueryService.toFilter(1,
                new Criteria(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), null, "roles", Action.UPDATE, null));

        assertThat(filter.from()).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00+07:00"));
        assertThat(filter.toExclusive()).isEqualTo(OffsetDateTime.parse("2026-11-08T00:00:00+07:00"));
        assertThat(filter.action()).isEqualTo("UPDATE");
        assertThat(filter.entityType()).isEqualTo("roles");
    }

    @Test
    @DisplayName("Không lọc gì: chỉ giới hạn theo công ty")
    void noCriteria() {
        Filter filter = AuditLogQueryService.toFilter(3, Criteria.none());

        assertThat(filter).isEqualTo(new Filter(3, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("Ngày bắt đầu sau ngày kết thúc, hoặc bảng lạ: lỗi 400")
    void invalidCriteria() {
        assertThatThrownBy(() -> AuditLogQueryService.toFilter(1,
                new Criteria(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7), null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AUDIT_RANGE_INVALID"));
        assertThatThrownBy(() -> AuditLogQueryService.toFilter(1,
                new Criteria(null, null, null, "salaries", null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("AUDIT_ENTITY_UNKNOWN"));
    }
}
