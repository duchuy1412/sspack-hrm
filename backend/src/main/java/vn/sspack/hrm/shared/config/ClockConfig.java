package vn.sspack.hrm.shared.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đồng hồ hệ thống theo múi giờ Asia/Ho_Chi_Minh. Mọi logic phụ thuộc thời gian inject {@link Clock}
 * thay vì gọi {@code now()} trực tiếp, để test điều khiển được thời gian.
 *
 * <p>Đồng hồ chỉ đếm tới micro giây, khớp với độ chính xác của {@code timestamptz} trong PostgreSQL.
 * Trên Linux, {@code Instant.now()} có nano giây; PostgreSQL làm tròn (không cắt) phần dư khi lưu, nên
 * một mốc như {@code expires_at} đọc lại có thể lớn hơn giá trị đã tính, làm sai so sánh ở đúng mốc
 * hết hạn (refresh token 7 ngày, khóa tài khoản 15 phút).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Bước nhỏ nhất của đồng hồ ứng dụng: 1 micro giây. */
    public static final Duration PRECISION = Duration.ofNanos(1_000);

    @Bean
    Clock clock() {
        return Clock.tick(Clock.system(ZONE), PRECISION);
    }
}
