package vn.sspack.hrm.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Test tích hợp: toàn bộ ứng dụng trên PostgreSQL 16 thật (Testcontainers), MockMvc và đồng hồ điều khiển
 * được. Mọi lớp test dùng chung một cấu hình nên Spring tái sử dụng context và container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "hrm.auth.jwt-secret=integration-test-jwt-secret-0123456789abcdef")
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfig.class, IntegrationTestConfig.class})
public @interface IntegrationTest {
}
