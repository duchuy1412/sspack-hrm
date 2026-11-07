package vn.sspack.hrm.platform.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import vn.sspack.hrm.platform.application.auth.AuthProperties;

/**
 * Bảo mật API (PLT-01): stateless, xác thực bằng access token JWT HS256 ở header {@code Authorization: Bearer}.
 *
 * <p>Authority gồm mã quyền ({@code leave:approve}) và vai trò ({@code ROLE_HR}), nên controller khai báo
 * {@code @PreAuthorize("hasAuthority('leave:approve')")}. Lọc theo phạm vi dữ liệu làm ở tầng service.
 *
 * <p>CSRF tắt: API không dùng session; cookie refresh token chỉ gửi tới {@code /api/v1/auth} với
 * {@code SameSite=Strict} nên trang khác không gửi kèm được.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemSecurityHandlers problemHandlers)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Chặn sớm ở tầng URL (kèm @PreAuthorize ở controller): kiểm tra quyền trước khi
                        // validate body, để người không có quyền nhận 403 chứ không thấy lỗi từng trường.
                        .requestMatchers("/api/v1/roles", "/api/v1/roles/**", "/api/v1/permissions")
                        .hasAuthority("role:manage")
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasAuthority("user:manage")
                        .requestMatchers("/api/v1/audit-logs", "/api/v1/audit-logs/**").hasAuthority("audit:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/departments", "/api/v1/departments/**")
                        .hasAuthority("org:read")
                        .requestMatchers("/api/v1/departments", "/api/v1/departments/**").hasAuthority("org:manage")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(SecurityConfig::toAuthentication))
                        .authenticationEntryPoint(problemHandlers))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer())));
        return decoder;
    }

    private static SecretKey secretKey(AuthProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        List<String> permissions = jwt.getClaimAsStringList(JwtAccessTokenIssuer.CLAIM_PERMISSIONS);
        List<String> roles = jwt.getClaimAsStringList(JwtAccessTokenIssuer.CLAIM_ROLES);
        if (permissions != null) {
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }
        if (roles != null) {
            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(JwtAccessTokenIssuer.CLAIM_EMAIL));
    }
}
