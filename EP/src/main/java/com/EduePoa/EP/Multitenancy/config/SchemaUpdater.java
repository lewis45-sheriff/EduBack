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
        try {
            normalizeTransportTypeValues();
        } catch (Exception e) {
            log.warn("Transport type normalization skipped (tables may not exist yet): {}", e.getMessage());
        }
    }

    private void widenRolePermissionsColumn() {
        jdbcTemplate.execute("ALTER TABLE role_permissions MODIFY COLUMN permission VARCHAR(50) NOT NULL");
        log.info("Ensured role_permissions.permission column is VARCHAR(50)");
    }

    /**
     * Normalizes legacy free-text transport_type values (e.g. "TwoWay", "OneWay", "one way")
     * to the canonical enum names ONE_WAY / TWO_WAY. Prior to per-term transport, transport_type
     * was a free-text String; it is now an {@code @Enumerated(STRING)} column, so any non-canonical
     * value causes "No enum constant" errors when Hibernate reads the row.
     */
    private void normalizeTransportTypeValues() {
        for (String table : new String[] {"student_transport", "transport_transactions"}) {
            if (!tableExists(table)) {
                continue;
            }
            // Anything containing "TWO" -> TWO_WAY, anything containing "ONE" -> ONE_WAY.
            int two = jdbcTemplate.update(
                    "UPDATE " + table + " SET transport_type = 'TWO_WAY' " +
                            "WHERE transport_type IS NOT NULL " +
                            "AND transport_type <> 'TWO_WAY' " +
                            "AND UPPER(transport_type) LIKE '%TWO%'");
            int one = jdbcTemplate.update(
                    "UPDATE " + table + " SET transport_type = 'ONE_WAY' " +
                            "WHERE transport_type IS NOT NULL " +
                            "AND transport_type <> 'ONE_WAY' " +
                            "AND UPPER(transport_type) LIKE '%ONE%'");
            if (one > 0 || two > 0) {
                log.info("Normalized transport_type in {}: {} -> ONE_WAY, {} -> TWO_WAY", table, one, two);
            }
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
