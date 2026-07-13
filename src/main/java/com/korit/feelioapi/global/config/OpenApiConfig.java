package com.korit.feelioapi.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 에 Bearer(JWT) 인증 스킴을 노출한다(문서 표기용).
 * 실제 런타임 인증은 BFF 방식으로 accessToken HttpOnly 쿠키를 JwtAuthenticationFilter 가 읽어 처리한다.
 * (/api/auth/token/refresh 는 permitAll 이라 토큰 없이도 동작)
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI feelioOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Feelio API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
