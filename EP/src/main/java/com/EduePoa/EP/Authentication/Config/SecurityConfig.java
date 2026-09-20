package com.EduePoa.EP.Authentication.Config;

import com.EduePoa.EP.Authentication.JWT.JwtAuthFilter;
import com.EduePoa.EP.Multitenancy.config.TenantResolverFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthenticationFilter;
    private final TenantResolverFilter tenantResolverFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CustomPermissionEvaluator customPermissionEvaluator;

    @Bean
    @Primary
    public DefaultSecurityFilterChain securityWebFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/refresh-token",
                                "/api/v1/bank/post-transactions/",
                                "/api/v1/process-call-back",
                                "/api/v1/transactions/c2b/confirmation",
                                "/api/v1/payments/stk-callback",
                                "/api/v1/validate",
                                // WebSocket handshake — the STOMP CONNECT frame is
                                // authenticated separately by StompAuthChannelInterceptor
                                "/ws/**",
                                // Uploaded public assets (school logos, etc.)
                                "/uploads/**"
                        ).permitAll()
                        // All other endpoints require authentication
                        // Permissions are handled by @PreAuthorize annotations in controllers
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantResolverFilter, JwtAuthFilter.class);

        return httpSecurity.build();
    }

     
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler =
                new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(customPermissionEvaluator);
        return expressionHandler;
    }
}