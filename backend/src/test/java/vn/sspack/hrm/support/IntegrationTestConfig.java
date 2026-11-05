package vn.sspack.hrm.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.sspack.hrm.shared.config.ClockConfig;

/** Bean dùng chung cho test tích hợp: đồng hồ điều khiển được thay cho đồng hồ hệ thống. */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now(), ClockConfig.ZONE);
    }
}
