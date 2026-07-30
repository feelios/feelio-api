package com.korit.feelioapi.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 운영 프론트 도메인. 프로필별로 다르므로 application.yaml 의 client.url 을 따른다.
    // (운영은 nginx 경로 프록시로 same-origin 이라 CORS 가 실제로 쓰이진 않지만,
    //  스킴이 어긋나면 차단되므로 하드코딩하지 않는다.)
    @Value("${client.url}")
    private String clientUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        clientUrl,
                        "http://localhost:5173",
                        "http://localhost:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
