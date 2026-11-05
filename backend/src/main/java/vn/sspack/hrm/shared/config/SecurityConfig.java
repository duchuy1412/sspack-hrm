package vn.sspack.hrm.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật TẠM THỜI cho giai đoạn dựng khung: mở mọi endpoint.
 *
 * <p>TODO(PLT-01, PLT-03): thay bằng xác thực JWT (access 15 phút, refresh 7 ngày) và phân quyền
 * {@code @PreAuthorize("hasAuthority('module:action')")} kèm lọc theo phạm vi dữ liệu.
 * {@link EnableMethodSecurity} đã bật sẵn để các controller khai báo quyền ngay từ đầu.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
