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
import org.springframework.security.core.context.SecurityContextHolder;


public class TenantEntityListener {

    private static final Logger log = LoggerFactory.getLogger(TenantEntityListener.class);

    @PrePersist
    public void prePersist(TenantScopedEntity entity) {
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
