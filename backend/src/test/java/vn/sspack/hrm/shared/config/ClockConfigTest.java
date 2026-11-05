package vn.sspack.hrm.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Đồng hồ ứng dụng chỉ đếm tới micro giây để mốc thời gian lưu vào {@code timestamptz} (PostgreSQL làm tròn
 * phần dư nano giây) đọc lại không bị lệch. Thiếu điều này, test hết hạn ở đúng mốc chập chờn trên Linux.
 */
class ClockConfigTest {

    final Clock clock = new ClockConfig().clock();

    @RepeatedTest(50)
    void neverProducesSubMicrosecondPrecision() {
        Instant now = clock.instant();

        assertThat(now.getNano() % 1_000).isZero();
    }

    @Test
    void usesVietnamTimeZone() {
        assertThat(clock.getZone()).isEqualTo(ClockConfig.ZONE);
        assertThat(ClockConfig.ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }
}
