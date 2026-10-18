package com.jev.filter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("chrome-extension://*", "http://localhost:[*]", "http://127.0.0.1:[*]")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type");
    }
}
