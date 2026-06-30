package com.EduePoa.EP.Multitenancy.config;

import com.EduePoa.EP.Authentication.JWT.JwtService;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Servlet filter that resolves the tenant context from the authenticated user's JWT token.
 * Runs AFTER JwtAuthFilter in the filter chain.
 *
 * <p>Resolution logic:</p>
 * <ul>
 *   <li>For authenticated requests: extracts {@code tenant_id} claim from the JWT token</li>
 *   <li>For Platform_Admin users: allows override via {@code X-Tenant-ID} header</li>
 *   <li>For non-Platform_Admin users: rejects requests with a differing {@code X-Tenant-ID} header (403)</li>
 *   <li>Validates that the resolved tenant exists and is ACTIVE</li>
 *   <li>Sets TenantContext with the resolved tenant identifier</li>
 *   <li>Clears TenantContext in finally block after filter chain completes</li>
 * </ul>
 *
 * @see TenantContext
 * @see com.EduePoa.EP.Authentication.JWT.JwtAuthFilter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantResolverFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String PLATFORM_ADMIN_ROLE = "ROLE_Platform_Admin";

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Skip if no authenticated user in SecurityContext (unauthenticated/public endpoints)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract JWT token from the authentication credentials
            // JwtAuthFilter stores the full token string as credentials
            Object credentials = authentication.getCredentials();
            if (credentials == null || !(credentials instanceof String jwtToken) || jwtToken.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract tenant_id claim from JWT
            String jwtTenantId = extractTenantIdFromToken(jwtToken);
            if (jwtTenantId == null || jwtTenantId.isBlank()) {
                log.warn("No tenant_id claim found in JWT for user: {}", authentication.getName());
                filterChain.doFilter(request, response);
                return;
            }

            // Resolve effective tenant considering X-Tenant-ID header and user role
            String headerTenantId = request.getHeader(TENANT_HEADER);
            String effectiveTenantId;

            if (headerTenantId != null && !headerTenantId.isBlank()) {
                boolean isPlatformAdmin = hasPlatformAdminRole(authentication);

                if (isPlatformAdmin) {
                    // Platform_Admin can override tenant context via header
                    effectiveTenantId = headerTenantId;
                    log.debug("Platform_Admin '{}' overriding tenant context to '{}'",
                            authentication.getName(), headerTenantId);
                } else {
                    // Non-admin: reject if header differs from JWT claim
                    if (!headerTenantId.equals(jwtTenantId)) {
                        log.warn("Non-admin user '{}' attempted tenant override: JWT='{}', Header='{}'",
                                authentication.getName(), jwtTenantId, headerTenantId);
                        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                "TENANT_OVERRIDE_DENIED",
                                "Only platform administrators can override tenant context",
                                request.getRequestURI());
                        return;
                    }
                    // Header matches JWT claim, no issue
                    effectiveTenantId = jwtTenantId;
                }
            } else {
                // No X-Tenant-ID header — use JWT claim
                effectiveTenantId = jwtTenantId;
            }

            // Validate tenant exists and is ACTIVE
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantIdentifier(effectiveTenantId);
            if (tenantOpt.isEmpty()) {
                log.warn("Tenant not found: '{}' for user: '{}'",
                        effectiveTenantId, authentication.getName());
                writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                        "TENANT_NOT_FOUND",
                        "Tenant not recognized",
                        request.getRequestURI());
                return;
            }

            Tenant tenant = tenantOpt.get();
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                log.warn("Inactive tenant '{}' (status: {}) accessed by user: '{}'",
                        effectiveTenantId, tenant.getStatus(), authentication.getName());
                writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                        "TENANT_INACTIVE",
                        "Tenant account is " + tenant.getStatus().name().toLowerCase(),
                        request.getRequestURI());
                return;
            }

            // Set tenant context
            TenantContext.setCurrentTenant(effectiveTenantId);
            log.debug("Tenant context set to '{}' for user: '{}'",
                    effectiveTenantId, authentication.getName());

            filterChain.doFilter(request, response);

        } finally {
            // Always clear tenant context to prevent leakage between requests
            TenantContext.clear();
        }
    }

    /**
     * Extracts the tenant_id claim from the JWT token.
     */
    private String extractTenantIdFromToken(String token) {
        try {
            return jwtService.extractClaim(token, claims -> claims.get("tenant_id", String.class));
        } catch (Exception e) {
            log.error("Failed to extract tenant_id from JWT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the authenticated user has the Platform_Admin role.
     */
    private boolean hasPlatformAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ADMIN_ROLE::equals);
    }

    /**
     * Writes a JSON error response.
     */
    private void writeErrorResponse(HttpServletResponse response, int status,
                                    String errorCode, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", status);
        errorBody.put("error", status == 403 ? "Forbidden" : "Unauthorized");
        errorBody.put("code", errorCode);
        errorBody.put("message", message);
        errorBody.put("path", path);

        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
