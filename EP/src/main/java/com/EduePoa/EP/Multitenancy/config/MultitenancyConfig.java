package com.EduePoa.EP.Multitenancy.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class for multi-tenancy components.
 * <p>
 * Registers the {@link HibernateFilterInterceptor} as a Spring MVC interceptor
 * so that the Hibernate tenant filter is enabled on every request before
 * controller processing begins.
 */
@Configuration
@RequiredArgsConstructor
public class MultitenancyConfig implements WebMvcConfigurer {

    private final HibernateFilterInterceptor hibernateFilterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hibernateFilterInterceptor);
    }
}
