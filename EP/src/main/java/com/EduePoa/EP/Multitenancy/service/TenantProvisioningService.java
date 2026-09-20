package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RolePermission;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantConfiguration;
import com.EduePoa.EP.Multitenancy.repository.TenantConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";
    private static final String SCHOOL_ADMIN_ROLE = "School_Admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantConfigurationRepository tenantConfigurationRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional
    public void provisionTenant(Tenant tenant) {
        String tenantIdentifier = tenant.getTenantIdentifier();
        String previousTenant = TenantContext.getCurrentTenant();

        try {
            // Set tenant context so TenantEntityListener populates tenant_id on User/Role
            TenantContext.setCurrentTenant(tenantIdentifier);

            createDefaultRoles(tenant);
            createDefaultAdminUser(tenant);
            initializeDefaultConfiguration(tenantIdentifier);

            log.info("Tenant provisioned successfully: identifier={}, schoolName={}",
                    tenantIdentifier, tenant.getSchoolName());
        } finally {
            // Restore previous tenant context
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private static final String TEACHER_ROLE = "ROLE_TEACHER";

    /**
     * Permissions granted to the default teacher role. Scoped to day-to-day
     * classroom duties: attendance, marks/exam entry, and read access to the
     * students, classes, subjects and reports a teacher works with.
     */
    private static final List<Permissions> TEACHER_PERMISSIONS = List.of(
            Permissions.ATTENDANCE_MARK,
            Permissions.ATTENDANCE_READ,
            Permissions.EXAM_READ,
            Permissions.EXAM_GRADE,
            Permissions.EXAM_MARK_ENTER,
            Permissions.CAT_MARK_ENTER,
            Permissions.STUDENT_READ,
            Permissions.CLASS_READ,
            Permissions.SUBJECT_READ,
            Permissions.REPORT_GENERATE,
            Permissions.COMMUNICATION_READ
    );

    /**
     * Creates default roles (ROLE_PARENT, SUPPLIER, ROLE_TEACHER) for the new tenant.
     * These roles are needed by other modules when creating parents/suppliers/teachers.
     */
    private void createDefaultRoles(Tenant tenant) {
        String tenantId = tenant.getTenantIdentifier();

        // Create ROLE_PARENT if it doesn't exist for this tenant
        if (roleRepository.findByNameAndTenantId("ROLE_PARENT", tenantId).isEmpty()) {
            Role parentRole = new Role();
            parentRole.setName("ROLE_PARENT");
            parentRole.setEnabledFlag('Y');
            parentRole.setDeletedFlag('N');
            parentRole.setStatus(Status.ACTIVE);
            roleRepository.save(parentRole);
            log.info("ROLE_PARENT created for tenant: {}", tenant.getTenantIdentifier());
        }

        // Create SUPPLIER role if it doesn't exist for this tenant
        if (roleRepository.findByNameAndTenantId("SUPPLIER", tenantId).isEmpty()) {
            Role supplierRole = new Role();
            supplierRole.setName("SUPPLIER");
            supplierRole.setEnabledFlag('Y');
            supplierRole.setDeletedFlag('N');
            supplierRole.setStatus(Status.ACTIVE);
            roleRepository.save(supplierRole);
            log.info("SUPPLIER role created for tenant: {}", tenant.getTenantIdentifier());
        }

        // Create ROLE_TEACHER if it doesn't exist for this tenant
        if (roleRepository.findByNameAndTenantId(TEACHER_ROLE, tenantId).isEmpty()) {
            Role teacherRole = new Role();
            teacherRole.setName(TEACHER_ROLE);
            teacherRole.setEnabledFlag('Y');
            teacherRole.setDeletedFlag('N');
            teacherRole.setStatus(Status.ACTIVE);
            for (Permissions permission : TEACHER_PERMISSIONS) {
                teacherRole.addPermission(permission);
            }
            roleRepository.save(teacherRole);
            log.info("ROLE_TEACHER created for tenant: {} with {} permissions",
                    tenant.getTenantIdentifier(), TEACHER_PERMISSIONS.size());
        }
    }

    private void createDefaultAdminUser(Tenant tenant) {
        String tenantIdentifier = tenant.getTenantIdentifier();
        String adminEmail = "admin@" + tenantIdentifier + ".edupoa.com";

        // Find or create the School_Admin role for this tenant
        Role schoolAdminRole = roleRepository.findByNameAndTenantId(SCHOOL_ADMIN_ROLE, tenantIdentifier)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(SCHOOL_ADMIN_ROLE);
                    newRole.setEnabledFlag('Y');
                    newRole.setDeletedFlag('N');
                    newRole.setStatus(Status.ACTIVE);

                    // Assign all school-level permissions (everything except MANAGE_TENANTS)
                    for (Permissions permission : Permissions.values()) {
                        if (permission != Permissions.MANAGE_TENANTS) {
                            newRole.addPermission(permission);
                        }
                    }

                    return roleRepository.save(newRole);
                });

        User adminUser = User.builder()
                .email(adminEmail)
                .username(adminEmail)
                .firstName("Admin")
                .lastName(tenant.getSchoolName())
                .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .role(schoolAdminRole)
                .status(Status.ACTIVE)
                .enabledFlag('Y')
                .deletedFlag('N')
                .is_lockedFlag('N')
                .forcePasswordReset(true)
                .passwordReset(true)
                .build();

        userRepository.save(adminUser);
        log.info("Default School_Admin user created: email={}, tenant={}",
                adminEmail, tenantIdentifier);
    }

    /**
     * Initializes default placeholder configuration entries for the tenant.
     * These are placeholders that the School_Admin will need to fill in.
     */
    private void initializeDefaultConfiguration(String tenantIdentifier) {
        List<TenantConfiguration> defaultConfigs = List.of(
                buildConfig(tenantIdentifier, "mpesa.shortcode", "", true),
                buildConfig(tenantIdentifier, "mpesa.passkey", "", true),
                buildConfig(tenantIdentifier, "mpesa.callback_url", "", false),
                buildConfig(tenantIdentifier, "sms.sender_id", "", false),
                buildConfig(tenantIdentifier, "sms.api_key", "", true),
                buildConfig(tenantIdentifier, "email.sender_address", "", false),
                buildConfig(tenantIdentifier, "email.sender_name", "", false),
                buildConfig(tenantIdentifier, "reports.school_name", "", false),
                buildConfig(tenantIdentifier, "reports.logo_url", "", false),
                buildConfig(tenantIdentifier, "reports.address", "", false)
        );

        tenantConfigurationRepository.saveAll(defaultConfigs);
        log.info("Default configuration entries initialized for tenant: {}", tenantIdentifier);
    }

    private TenantConfiguration buildConfig(String tenantIdentifier, String key,
                                            String value, boolean isSensitive) {
        return TenantConfiguration.builder()
                .tenantIdentifier(tenantIdentifier)
                .configKey(key)
                .configValue(value)
                .isSensitive(isSensitive)
                .build();
    }
}
