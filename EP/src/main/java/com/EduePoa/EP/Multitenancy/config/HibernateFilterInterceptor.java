package com.EduePoa.EP.Multitenancy.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that enables the Hibernate tenant filter on each request.
 * <p>
 * In {@code preHandle}, this interceptor determines whether to enable the
 * {@code tenantFilter} on the current Hibernate session:
 * <ul>
 *   <li>Non-Platform_Admin users: filter is always enabled with the resolved tenant identifier</li>
 *   <li>Platform_Admin without {@code X-Tenant-ID} header: filter is NOT enabled, allowing cross-tenant queries</li>
 *   <li>Platform_Admin with {@code X-Tenant-ID} header: filter IS enabled, scoping queries to the specified tenant</li>
 * </ul>
 * <p>
 * In {@code afterCompletion}, the tenant context is cleared as a safety net to prevent
 * tenant leakage between requests.
 */
@Slf4j
@Component
public class HibernateFilterInterceptor implements HandlerInterceptor {

    private static final String TENANT_FILTER_NAME = "tenantFilter";
    private static final String TENANT_FILTER_PARAM = "tenantId";
    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_Platform_Admin";
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!TenantContext.isSet()) {
            log.debug("TenantContext not set, skipping Hibernate filter for: {}", request.getRequestURI());
            return true;
        }

        boolean platformAdmin = isPlatformAdmin();

        if (platformAdmin) {
            String tenantHeader = request.getHeader(TENANT_HEADER);
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                log.debug("Platform_Admin with X-Tenant-ID header, enabling filter for tenant: {}", TenantContext.getCurrentTenant());
                enableTenantFilter(TenantContext.getCurrentTenant());
            } else {
                log.debug("Platform_Admin without X-Tenant-ID header, bypassing filter for cross-tenant access");
            }
        } else {
            log.info("Enabling tenant filter for tenant: {} on path: {}", TenantContext.getCurrentTenant(), request.getRequestURI());
            enableTenantFilter(TenantContext.getCurrentTenant());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    /**
     * Enables the Hibernate tenant filter on the current session with the given tenant identifier.
     *
     * @param tenantId the tenant identifier to scope queries to
     */
    private void enableTenantFilter(String tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TENANT_FILTER_NAME)
                .setParameter(TENANT_FILTER_PARAM, tenantId);
    }

    /**
     * Checks whether the currently authenticated user has the Platform_Admin role.
     *
     * @return {@code true} if the user is a Platform_Admin, {@code false} otherwise
     */
    private boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ADMIN_AUTHORITY::equals);
    }
}
