package vn.sspack.hrm.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI hrmOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SS Pack HRM API")
                .version("v1")
                .description("API hệ thống quản lý nhân sự SS Pack. Mọi endpoint nghiệp vụ có tiền tố /api/v1."));
    }
}
