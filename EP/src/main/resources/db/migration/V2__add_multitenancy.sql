-- V2__add_multitenancy.sql
-- Multi-tenancy schema migration for EduPoa
-- Adds tenant_id discriminator column to all tenant-scoped tables,
-- creates tenant management tables, and updates unique constraints.
-- Validates: Requirements 3.1, 7.1, 7.2, 7.3

-- ============================================================
-- 1. Create tenants table
-- ============================================================
CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(63) NOT NULL,
    school_name VARCHAR(255) NOT NULL,
    physical_address VARCHAR(500),
    email_domain VARCHAR(255),
    phone_number VARCHAR(20),
    logo_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'BASIC',
    created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_tenants_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. Insert default tenant for existing data (Bureti High School)
-- ============================================================
INSERT INTO tenants (tenant_id, school_name, status, subscription_plan)
VALUES ('bureti-high', 'Bureti High School', 'ACTIVE', 'PREMIUM')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

-- ============================================================
-- 3. Add tenant_id column to all tenant-scoped tables
--    Uses DEFAULT 'bureti-high' so existing rows get the default tenant
-- ============================================================

-- Authentication module
ALTER TABLE user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Student module
ALTER TABLE student ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE student_guardian ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE student_nemis ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE parent ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Grade module
ALTER TABLE grade ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Fee module
ALTER TABLE fee_structure ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE fee_components ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Finance module
ALTER TABLE finance ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE finance_transactions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE student_invoices ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Transport module
ALTER TABLE transport ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE transport_transactions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE student_transport ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- M-Pesa module
ALTER TABLE mpesa_stk_transaction ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE mpesa_paybill ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Communications module
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE messages ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE message_recipients ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Procurement module
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE delivery_note_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE supplier_payments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE stock_requisitions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE stock_requisition_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Scoring module
ALTER TABLE exam_type ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE exam_type_grading ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE students_score ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Academics module
ALTER TABLE academic_subject ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE class_subject_assignment ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';
ALTER TABLE cbc_grade_result ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';

-- Audit logs (system-level but tenant-scoped for filtering)
ALTER TABLE audit ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'bureti-high';


-- ============================================================
-- 4. Update unique constraints to be composite with tenant_id
--    Email uniqueness is per-tenant, not global
--    Role name uniqueness is per-tenant, not global
-- ============================================================

-- User: drop old email unique index if exists, add composite (email, tenant_id)
-- MariaDB stores unique constraints as indexes
DROP INDEX IF EXISTS email ON user;
DROP INDEX IF EXISTS UK_ob8kqyqqgmefl0aco34akdtpe ON user;  -- Hibernate-generated name
DROP INDEX IF EXISTS idx_user_email_tenant ON user;
CREATE UNIQUE INDEX idx_user_email_tenant ON user (email, tenant_id);

-- Role: drop old name unique index if exists, add composite (name, tenant_id)
DROP INDEX IF EXISTS name ON role;
DROP INDEX IF EXISTS UK_8sewwnpamngi6b1dwaa88askk ON role;  -- Hibernate-generated name
DROP INDEX IF EXISTS idx_role_name_tenant ON role;
CREATE UNIQUE INDEX idx_role_name_tenant ON role (name, tenant_id);

-- ============================================================
-- 5. Add indexes on tenant_id columns for query performance
-- ============================================================

-- Authentication
CREATE INDEX IF NOT EXISTS idx_user_tenant ON user (tenant_id);
CREATE INDEX IF NOT EXISTS idx_role_tenant ON role (tenant_id);

-- Students
CREATE INDEX IF NOT EXISTS idx_student_tenant ON student (tenant_id);
CREATE INDEX IF NOT EXISTS idx_student_guardian_tenant ON student_guardian (tenant_id);
CREATE INDEX IF NOT EXISTS idx_student_nemis_tenant ON student_nemis (tenant_id);
CREATE INDEX IF NOT EXISTS idx_parent_tenant ON parent (tenant_id);

-- Grade
CREATE INDEX IF NOT EXISTS idx_grade_tenant ON grade (tenant_id);

-- Fees
CREATE INDEX IF NOT EXISTS idx_fee_structure_tenant ON fee_structure (tenant_id);
CREATE INDEX IF NOT EXISTS idx_fee_components_tenant ON fee_components (tenant_id);

-- Finance
CREATE INDEX IF NOT EXISTS idx_finance_tenant ON finance (tenant_id);
CREATE INDEX IF NOT EXISTS idx_finance_transactions_tenant ON finance_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_student_invoices_tenant ON student_invoices (tenant_id);
CREATE INDEX IF NOT EXISTS idx_expenses_tenant ON expenses (tenant_id);

-- Transport
CREATE INDEX IF NOT EXISTS idx_transport_tenant ON transport (tenant_id);
CREATE INDEX IF NOT EXISTS idx_transport_transactions_tenant ON transport_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_student_transport_tenant ON student_transport (tenant_id);

-- M-Pesa
CREATE INDEX IF NOT EXISTS idx_mpesa_stk_transaction_tenant ON mpesa_stk_transaction (tenant_id);
CREATE INDEX IF NOT EXISTS idx_mpesa_paybill_tenant ON mpesa_paybill (tenant_id);

-- Communications
CREATE INDEX IF NOT EXISTS idx_announcements_tenant ON announcements (tenant_id);
CREATE INDEX IF NOT EXISTS idx_messages_tenant ON messages (tenant_id);
CREATE INDEX IF NOT EXISTS idx_message_recipients_tenant ON message_recipients (tenant_id);
CREATE INDEX IF NOT EXISTS idx_attachments_tenant ON attachments (tenant_id);

-- Procurement
CREATE INDEX IF NOT EXISTS idx_purchase_orders_tenant ON purchase_orders (tenant_id);
CREATE INDEX IF NOT EXISTS idx_purchase_order_items_tenant ON purchase_order_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_delivery_notes_tenant ON delivery_notes (tenant_id);
CREATE INDEX IF NOT EXISTS idx_delivery_note_items_tenant ON delivery_note_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_supplier_invoices_tenant ON supplier_invoices (tenant_id);
CREATE INDEX IF NOT EXISTS idx_supplier_invoice_items_tenant ON supplier_invoice_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_tenant ON supplier_payments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_suppliers_tenant ON suppliers (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_tenant ON ledger_entries (tenant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_items_tenant ON inventory_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_tenant ON inventory_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_requisitions_tenant ON stock_requisitions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_requisition_items_tenant ON stock_requisition_items (tenant_id);

-- Scoring
CREATE INDEX IF NOT EXISTS idx_exam_type_tenant ON exam_type (tenant_id);
CREATE INDEX IF NOT EXISTS idx_exam_type_grading_tenant ON exam_type_grading (tenant_id);
CREATE INDEX IF NOT EXISTS idx_students_score_tenant ON students_score (tenant_id);

-- Academics
CREATE INDEX IF NOT EXISTS idx_academic_subject_tenant ON academic_subject (tenant_id);
CREATE INDEX IF NOT EXISTS idx_class_subject_assignment_tenant ON class_subject_assignment (tenant_id);
CREATE INDEX IF NOT EXISTS idx_cbc_grade_result_tenant ON cbc_grade_result (tenant_id);

-- Audit
CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit (tenant_id);

-- ============================================================
-- 5b. Widen role_permissions.permission column to VARCHAR(255)
--     The column may have been created as a MariaDB ENUM by Hibernate's
--     ddl-auto=update, which doesn't auto-expand when new Java enum values
--     are added. Converting to VARCHAR(255) allows future enum additions.
-- ============================================================
ALTER TABLE role_permissions MODIFY COLUMN permission VARCHAR(255) NOT NULL;

-- ============================================================
-- 6. Create tenant_configurations table
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(63) NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    is_sensitive BOOLEAN DEFAULT FALSE,
    UNIQUE INDEX idx_tenant_config (tenant_id, config_key),
    CONSTRAINT fk_tenant_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. Create tenant_audit_logs table
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(63) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_audit_logs_tenant (tenant_id),
    INDEX idx_tenant_audit_logs_action (action),
    CONSTRAINT fk_tenant_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
