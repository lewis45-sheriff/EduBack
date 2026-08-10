package com.EduePoa.EP.Multitenancy.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect that enables the Hibernate tenant filter on the current session
 * before any JPA repository method executes. This ensures the filter is active
 * even inside @Transactional boundaries where the session may differ from
 * the one the MVC interceptor enabled it on.
 */
@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    private static final String TENANT_FILTER_NAME = "tenantFilter";
    private static final String TENANT_FILTER_PARAM = "tenantId";
    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_Platform_Admin";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Before any Spring Data JPA repository method executes, enable the tenant filter
     * on the current Hibernate session if TenantContext is set and user is not Platform_Admin.
     */
    @Before("execution(* com.EduePoa.EP..*.*(..)) && " +
            "target(org.springframework.data.jpa.repository.JpaRepository)")
    public void enableTenantFilter() {
        if (!TenantContext.isSet()) {
            return;
        }

        if (isPlatformAdmin()) {
            return;
        }

        try {
            Session session = entityManager.unwrap(Session.class);
            if (session.getEnabledFilter(TENANT_FILTER_NAME) == null) {
                session.enableFilter(TENANT_FILTER_NAME)
                        .setParameter(TENANT_FILTER_PARAM, TenantContext.getCurrentTenant());
                log.debug("Tenant filter enabled via aspect for tenant: {}", TenantContext.getCurrentTenant());
            }
        } catch (Exception e) {
            log.warn("Could not enable tenant filter via aspect: {}", e.getMessage());
        }
    }

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
