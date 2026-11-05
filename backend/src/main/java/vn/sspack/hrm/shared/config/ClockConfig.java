package vn.sspack.hrm.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đồng hồ hệ thống theo múi giờ Asia/Ho_Chi_Minh. Mọi logic phụ thuộc thời gian inject {@link Clock}
 * thay vì gọi {@code now()} trực tiếp, để test điều khiển được thời gian.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
