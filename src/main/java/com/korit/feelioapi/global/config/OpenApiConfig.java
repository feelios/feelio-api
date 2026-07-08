package com.korit.feelioapi.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 에 Bearer(JWT) 인증 스킴을 노출한다.
 * 보호 API 는 런타임에 JwtAuthenticationFilter 가 검사하지만, springdoc 문서엔 그 정보가 없어
 * Authorize 버튼이 뜨지 않는다. 이 스킴 선언으로 Authorize 에 accessToken 을 넣으면
 * 이후 요청에 Authorization: Bearer &lt;token&gt; 이 자동으로 실린다.
 * (/api/auth/login·token/refresh 는 permitAll 이라 토큰 없이도 동작)
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
