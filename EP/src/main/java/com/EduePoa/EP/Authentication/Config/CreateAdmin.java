package com.EduePoa.EP.Authentication.Config;

import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RolePermission;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.Role.RoleService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import com.EduePoa.EP.Utils.ResourceNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

@Slf4j
@Component
@AllArgsConstructor
@Order(10)
public class CreateAdmin implements ApplicationRunner {

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultTenantExists();
        addAdminRole();
        addAdmin();
//        addParentsRole();
        addSupplier();
        addPlatformAdminRole();
        addPlatformAdmin();
    }

    /**
     * Ensures the default tenant (bureti-high) exists so that seeded users
     * can be assigned a tenant_id. This is needed because User extends TenantScopedEntity.
     */
    void ensureDefaultTenantExists() {
        if (!tenantRepository.existsByTenantIdentifier("bureti-high")) {
            Tenant tenant = new Tenant();
            tenant.setTenantIdentifier("bureti-high");
            tenant.setSchoolName("Bureti High School");
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setSubscriptionPlan("PREMIUM");
            tenantRepository.save(tenant);
            log.info("Default tenant 'bureti-high' created.");
        }
    }

    private static final String DEFAULT_TENANT = "bureti-high";

    void addAdminRole() {
        try {
            TenantContext.setCurrentTenant(DEFAULT_TENANT);
            Optional<Role> existingRole = roleRepository.findByNameAndTenantId("ROLE_ADMIN", DEFAULT_TENANT);

            if (existingRole.isEmpty()) {
                log.info("Creating ROLE_ADMIN role on " + LocalDateTime.now());
                roleService.createRole("ROLE_ADMIN");
            } else {
                log.info("ROLE_ADMIN role already exists. Updating permissions...");
                roleService.updateRolePermissions(existingRole.get());
            }
        } finally {
            TenantContext.clear();
        }
    }
//    void addParentsRole() {
//        try {
//            TenantContext.setCurrentTenant("bureti-high");
//            if (roleRepository.findByName("ROLE_PARENT").isEmpty()) {
//                log.info("Creating ROLE_PARENT role on " + LocalDateTime.now());
//                roleService.createRole("ROLE_PARENT");
//            } else {
//                log.info("ROLE_PARENT role already exists.");
//            }
//        } finally {
//            TenantContext.clear();
//        }
//    }
    void addSupplier() {
        try {
            TenantContext.setCurrentTenant(DEFAULT_TENANT);
            Optional<Role> existingRole = roleRepository.findByNameAndTenantId("SUPPLIER", DEFAULT_TENANT);

            if (existingRole.isEmpty()) {
                log.info("Creating SUPPLIER role on " + LocalDateTime.now());
                roleService.createRole("SUPPLIER");
            } else {
                log.info("SUPPLIER role already exists. Updating permissions...");
                // roleService.updateRolePermissionsSupplier(existingRole.get());
            }
        } finally {
            TenantContext.clear();
        }
    }

    void addAdmin() {
        try {
            // Set tenant context so TenantEntityListener can populate tenant_id
            TenantContext.setCurrentTenant(DEFAULT_TENANT);

            Integer adminCount = userRepository.adminCount("ROLE_ADMIN");

            if (adminCount > 0) {
                log.info("System admin already exists.");
            } else {
                Role adminRole = roleRepository.findByNameAndTenantId("ROLE_ADMIN", DEFAULT_TENANT)
                        .orElseThrow(() -> new ResourceNotFoundException("Role with name ROLE_ADMIN not found"));

                User user = new User();
                user.setUsername("Admin");
                user.setEmail("lewiskipkemoi53@gmail.com");
                user.setFirstName("Super");
                user.setLastName("Admin");
                user.setPassword(passwordEncoder.encode("1234"));
                user.setEnabledFlag('Y');
                user.setStatus(Status.ACTIVE);
                user.setRole(adminRole);
                user.setLocation("EM-TECH");
                user.setGender("null");
                user.setDeletedFlag('N');
                userRepository.save(user);

                log.info("System admin created successfully.");
            }
        } catch (Exception e) {
            log.error("Error while creating admin: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Seeds the Platform_Admin role with the MANAGE_TENANTS permission.
     * This role grants cross-tenant access and tenant management capabilities.
     */
    void addPlatformAdminRole() {
        try {
            // Set tenant context for role creation (roles are tenant-scoped)
            TenantContext.setCurrentTenant(DEFAULT_TENANT);

            Optional<Role> existingRole = roleRepository.findByNameAndTenantId("Platform_Admin", DEFAULT_TENANT);

            if (existingRole.isEmpty()) {
                log.info("Creating Platform_Admin role...");

                Role role = new Role();
                role.setName("Platform_Admin");
                role.setEnabledFlag('Y');
                role.setDeletedFlag('N');
                role.setStatus(Status.ACTIVE);
                role.setRolePermissions(new HashSet<>());

                // Only add MANAGE_TENANTS permission — scoped to tenant onboarding only
                RolePermission tenantPermission = new RolePermission(role, Permissions.MANAGE_TENANTS);
                role.getRolePermissions().add(tenantPermission);

                roleRepository.save(role);
                log.info("Platform_Admin role created with MANAGE_TENANTS permission only.");
            } else {
                log.info("Platform_Admin role already exists.");
            }
        } catch (Exception e) {
            log.error("Error creating Platform_Admin role: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Seeds a default Platform_Admin user that can manage tenants.
     * This user has cross-tenant access and is assigned to the default tenant.
     */
    void addPlatformAdmin() {
        try {
            TenantContext.setCurrentTenant(DEFAULT_TENANT);

            Optional<User> existing = userRepository.findByEmail("platform@edupoa.com");

            if (existing.isPresent()) {
                log.info("Platform_Admin user already exists.");
                return;
            }

            Role platformAdminRole = roleRepository.findByNameAndTenantId("Platform_Admin", DEFAULT_TENANT)
                    .orElseThrow(() -> new ResourceNotFoundException("Role 'Platform_Admin' not found"));

            User platformAdmin = new User();
            platformAdmin.setUsername("platform@edupoa.com");
            platformAdmin.setEmail("platform@edupoa.com");
            platformAdmin.setFirstName("Platform");
            platformAdmin.setLastName("Administrator");
            platformAdmin.setPassword(passwordEncoder.encode("Platform@2026!"));
            platformAdmin.setEnabledFlag('Y');
            platformAdmin.setStatus(Status.ACTIVE);
            platformAdmin.setRole(platformAdminRole);
            platformAdmin.setLocation("EduPoa HQ");
            platformAdmin.setGender("null");
            platformAdmin.setDeletedFlag('N');
            platformAdmin.setForcePasswordReset(true);
            userRepository.save(platformAdmin);

            log.info("Platform_Admin user created: email=platform@edupoa.com");
        } catch (Exception e) {
            log.error("Error creating Platform_Admin user: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
