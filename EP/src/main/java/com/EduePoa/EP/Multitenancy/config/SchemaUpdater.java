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
            ensureStudentTransportTermColumns();
        } catch (Exception e) {
            log.warn("student_transport term/year column check skipped (table may not exist yet): {}", e.getMessage());
        }
        try {
            ensureFinanceTransactionColumns();
        } catch (Exception e) {
            log.warn("finance_transactions column check skipped (table may not exist yet): {}", e.getMessage());
        }
        try {
            normalizeTransportTypeValues();
        } catch (Exception e) {
            log.warn("Transport type normalization skipped (tables may not exist yet): {}", e.getMessage());
        }
        try {
            backfillPayrollPermissions();
        } catch (Exception e) {
            log.warn("Payroll permission backfill skipped (tables may not exist yet): {}", e.getMessage());
        }
    }

    /**
     * Grants the segregated HR &amp; Payroll permissions to every pre-existing full-access role
     * (ROLE_ADMIN and each tenant's School_Admin). New permissions added to the {@code Permissions}
     * enum are only auto-granted to roles created/re-synced after the enum change; existing
     * tenants' School_Admin rows are never force-re-synced, so without this backfill those admins
     * would silently lack the new payroll:process / payroll:approve / payroll:config / payroll:read /
     * payroll:self:read authorities. This is a data operation (not a schema change), kept here so it
     * runs once per boot alongside the other idempotent adjustments. Each grant is guarded by a
     * NOT EXISTS check, so it is safe to run repeatedly.
     * <p>
     * The {@code permission} column stores the enum CONSTANT NAME (JPA {@code @Enumerated(STRING)}),
     * so we insert the constant names, not the colon-delimited authority strings.
     */
    private void backfillPayrollPermissions() {
        if (!tableExists("role_permissions") || !tableExists("role")) {
            return;
        }
        String[] constantNames = {
                "PAYROLL_PROCESS", "PAYROLL_APPROVE", "PAYROLL_CONFIG",
                "PAYROLL_READ", "PAYROLL_SELF_READ"
        };
        // Only full-access administrative roles are backfilled. Narrow roles (teacher, parent,
        // supplier, platform admin) are intentionally excluded so segregation of duties and the
        // self-service boundary are not accidentally widened.
        String[] roleNames = {"ROLE_ADMIN", "School_Admin"};
        int granted = 0;
        for (String roleName : roleNames) {
            for (String constant : constantNames) {
                granted += jdbcTemplate.update(
                        "INSERT INTO role_permissions (role_id, permission, tenant_id) " +
                                "SELECT r.id, ?, r.tenant_id FROM role r " +
                                "WHERE r.name = ? " +
                                "AND NOT EXISTS (" +
                                "  SELECT 1 FROM role_permissions rp " +
                                "  WHERE rp.role_id = r.id AND rp.permission = ?" +
                                ")",
                        constant, roleName, constant);
            }
        }
        if (granted > 0) {
            log.info("Backfilled {} payroll permission grants onto existing admin roles", granted);
        }
    }

    /**
     * Ensures the per-term columns on {@code student_transport} exist. Hibernate's
     * {@code ddl-auto=update} does not reliably add these columns on an existing table
     * (the unique constraint on {student_id, term, year} references them), which causes
     * "Unknown column 'term'" errors at query time. These columns are nullable for
     * backward compatibility with pre per-term rows.
     */
    private void ensureStudentTransportTermColumns() {
        if (!tableExists("student_transport")) {
            return;
        }
        if (!columnExists("student_transport", "term")) {
            jdbcTemplate.execute("ALTER TABLE student_transport ADD COLUMN term VARCHAR(50) NULL");
            log.info("Added missing column student_transport.term");
        }
        if (!columnExists("student_transport", "year")) {
            jdbcTemplate.execute("ALTER TABLE student_transport ADD COLUMN year INT NULL");
            log.info("Added missing column student_transport.year");
        }
    }

    /**
     * Ensures columns added to the {@code FinanceTransaction} entity after the table was first
     * created exist in the database. Hibernate's {@code ddl-auto=update} is unreliable at adding
     * columns to an existing table, which causes "Unknown column 'transfer_group_id'" (and similar)
     * errors when listing transactions. All columns are nullable for backward compatibility.
     */
    private void ensureFinanceTransactionColumns() {
        if (!tableExists("finance_transactions")) {
            return;
        }
        if (!columnExists("finance_transactions", "transfer_group_id")) {
            jdbcTemplate.execute("ALTER TABLE finance_transactions ADD COLUMN transfer_group_id BIGINT NULL");
            log.info("Added missing column finance_transactions.transfer_group_id");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
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
