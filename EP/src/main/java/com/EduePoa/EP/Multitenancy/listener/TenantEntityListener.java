package com.EduePoa.EP.Multitenancy.listener;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.config.TenantContextMissingException;
import com.EduePoa.EP.Multitenancy.config.TenantMismatchException;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;


public class TenantEntityListener {

    private static final Logger log = LoggerFactory.getLogger(TenantEntityListener.class);

    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_Platform_Admin";

    /**
     * Whether the current request is a cross-tenant (platform) operation. This is true when either
     * the {@link TenantContext} cross-tenant flag is set, OR the authenticated principal holds the
     * Platform_Admin authority. The role check is the resilient signal: intermediate code (e.g.
     * tenant provisioning / curriculum seeding) legitimately re-points and clears the thread's
     * tenant context, which would wipe the flag before the JPA flush runs the lifecycle callbacks.
     * The Security context authority is stable for the whole request, so a Platform_Admin's writes
     * are never incorrectly blocked by the single-tenant isolation guard.
     */
    private boolean isCrossTenantOperation() {
        if (TenantContext.isCrossTenant()) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ADMIN_AUTHORITY::equals);
    }

    @PrePersist
    public void prePersist(TenantScopedEntity entity) {
        // Cross-tenant (platform) mode: a Platform_Admin may create entities that belong to any
        // tenant (e.g. managing the tenants catalogue). Respect a tenant_id already set on the
        // entity and do not force it to the admin's own JWT tenant. Only stamp it from context
        // when the entity hasn't specified one.
        if (isCrossTenantOperation()) {
            if (entity.getTenantId() == null && TenantContext.isSet()) {
                entity.setTenantId(TenantContext.getCurrentTenant());
            }
            return;
        }

        if (!TenantContext.isSet()) {
            log.warn("SECURITY_WARN tenant_context_missing | entity_type={} | user={} | action=PERSIST",
                    entity.getClass().getSimpleName(),
                    getCurrentUsername());
            throw new TenantContextMissingException();
        }

        String contextTenant = TenantContext.getCurrentTenant();
        entity.setTenantId(contextTenant);
    }

    @PreUpdate
    public void preUpdate(TenantScopedEntity entity) {
        // Cross-tenant (platform) mode: a Platform_Admin legitimately updates entities belonging
        // to other tenants, so the single-tenant match guard does not apply. The entity's own
        // tenant_id is left untouched (updatable=false on the column already prevents reassignment).
        if (isCrossTenantOperation()) {
            return;
        }

        if (!TenantContext.isSet()) {
            log.warn("SECURITY_WARN tenant_context_missing | entity_type={} | user={} | action=UPDATE",
                    entity.getClass().getSimpleName(),
                    getCurrentUsername());
            throw new TenantContextMissingException();
        }

        String contextTenant = TenantContext.getCurrentTenant();
        String entityTenant = entity.getTenantId();

        if (entityTenant != null && !entityTenant.equals(contextTenant)) {
            log.warn("SECURITY_WARN tenant_mismatch | context_tenant={} | entity_tenant={} | entity_type={} | user={} | action=UPDATE",
                    contextTenant,
                    entityTenant,
                    entity.getClass().getSimpleName(),
                    getCurrentUsername());
            throw new TenantMismatchException(contextTenant, entityTenant);
        }
    }


    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "anonymous";
    }
}
