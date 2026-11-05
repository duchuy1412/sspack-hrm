package vn.sspack.hrm.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI hrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SS Pack HRM API")
                        .version("v1")
                        .description("API hệ thống quản lý nhân sự SS Pack. Mọi endpoint nghiệp vụ có tiền tố /api/v1. "
                                + "Gọi POST /api/v1/auth/login, rồi bấm Authorize và dán access token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
