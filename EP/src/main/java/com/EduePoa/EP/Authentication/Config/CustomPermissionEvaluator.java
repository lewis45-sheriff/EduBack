package com.EduePoa.EP.Authentication.Config;



import com.EduePoa.EP.Authentication.User.User;
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
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Authentication is null or not authenticated");
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            String requiredPermission = permission.toString();

            // Check if user's role has the required permission
            boolean hasPermission = user.getRole().getRolePermissions().stream()
                    .anyMatch(rp -> rp.getPermission().getPermission().equals(requiredPermission));

            log.debug("User: {} checking permission: {} - Result: {}",
                    user.getEmail(), requiredPermission, hasPermission);

            return hasPermission;
        } catch (Exception e) {
            log.error("Error checking permission: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }
}