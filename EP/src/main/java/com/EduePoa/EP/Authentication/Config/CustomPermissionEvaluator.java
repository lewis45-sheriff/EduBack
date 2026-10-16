package com.EduePoa.EP.Authentication.Config;



import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        String requiredPermission = permission != null ? permission.toString() : "<null>";
        String tenant = safeTenant();

        if (authentication == null || !authentication.isAuthenticated()) {
            // Denied before we even know the user — log it so unauthenticated access attempts
            // against protected endpoints are visible.
            log.warn("PERMISSION DENIED: user=<unauthenticated> permission='{}' tenant='{}' (no authenticated principal)",
                    requiredPermission, tenant);
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            String userEmail = user.getEmail();
            String roleName = user.getRole() != null ? user.getRole().getName() : "<no-role>";

            boolean hasPermission = user.getRole() != null
                    && user.getRole().getRolePermissions().stream()
                    .anyMatch(rp -> rp.getPermission().getPermission().equals(requiredPermission));

            if (hasPermission) {
                // Granted checks are high-volume (every @PreAuthorize call), so keep them at DEBUG.
                log.debug("PERMISSION GRANTED: user='{}' role='{}' permission='{}' tenant='{}'",
                        userEmail, roleName, requiredPermission, tenant);
            } else {
                // Denials are the security-relevant signal — log at WARN so they're visible at
                // default log levels without flooding logs with grants.
                log.warn("PERMISSION DENIED: user='{}' role='{}' permission='{}' tenant='{}'",
                        userEmail, roleName, requiredPermission, tenant);
            }

            return hasPermission;
        } catch (Exception e) {
            log.error("PERMISSION CHECK ERROR: permission='{}' tenant='{}' error={}",
                    requiredPermission, tenant, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }

    /** Current tenant for log context; never throws. */
    private static String safeTenant() {
        try {
            String tenant = TenantContext.getCurrentTenant();
            return tenant != null ? tenant : "<none>";
        } catch (Exception e) {
            return "<none>";
        }
    }
}
