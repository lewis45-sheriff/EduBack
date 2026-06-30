package com.EduePoa.EP.Multitenancy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs schema adjustments that Hibernate's ddl-auto=update cannot perform automatically.
 * Specifically, widens the role_permissions.permission column from MariaDB ENUM to VARCHAR(50)
 * to accommodate new permission enum values (e.g., MANAGE_TENANTS).
 *
 * Must run before CreateAdmin which inserts permission values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaUpdater implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            widenRolePermissionsColumn();
        } catch (Exception e) {
            log.warn("Schema update for role_permissions skipped (table may not exist yet): {}", e.getMessage());
        }
    }

    private void widenRolePermissionsColumn() {
        jdbcTemplate.execute("ALTER TABLE role_permissions MODIFY COLUMN permission VARCHAR(50) NOT NULL");
        log.info("Ensured role_permissions.permission column is VARCHAR(50)");
    }
}
