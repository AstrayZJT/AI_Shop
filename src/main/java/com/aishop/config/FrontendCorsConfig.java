package com.aishop.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FrontendCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;
    private final List<String> allowedOriginPatterns;

    public FrontendCorsConfig(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") List<String> allowedOriginPatterns) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        this.allowedOriginPatterns = allowedOriginPatterns.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty() && allowedOriginPatterns.isEmpty()) {
            return;
        }
        var registration = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        if (!allowedOrigins.isEmpty()) {
            registration.allowedOrigins(allowedOrigins.toArray(String[]::new));
        }
        if (!allowedOriginPatterns.isEmpty()) {
            registration.allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
        }
    }
}
