-- U2__add_multitenancy.sql
-- Rollback script for V2__add_multitenancy.sql
-- Reverses all multi-tenancy schema changes and restores original single-tenant schema
-- NOTE: This is a Flyway Undo migration (requires Flyway Teams edition)

-- ============================================================
-- 1. Drop tenant_audit_logs and tenant_configurations tables (FK deps first)
-- ============================================================
DROP TABLE IF EXISTS tenant_audit_logs;
DROP TABLE IF EXISTS tenant_configurations;

-- ============================================================
-- 2. Drop tenant_id indexes from all tenant-scoped tables
-- ============================================================

-- Authentication
DROP INDEX IF EXISTS idx_user_tenant ON user;
DROP INDEX IF EXISTS idx_role_tenant ON role;

-- Students
DROP INDEX IF EXISTS idx_student_tenant ON student;
DROP INDEX IF EXISTS idx_student_guardian_tenant ON student_guardian;
DROP INDEX IF EXISTS idx_student_nemis_tenant ON student_nemis;
DROP INDEX IF EXISTS idx_parent_tenant ON parent;

-- Grade
DROP INDEX IF EXISTS idx_grade_tenant ON grade;

-- Fees
DROP INDEX IF EXISTS idx_fee_structure_tenant ON fee_structure;
DROP INDEX IF EXISTS idx_fee_components_tenant ON fee_components;

-- Finance
DROP INDEX IF EXISTS idx_finance_tenant ON finance;
DROP INDEX IF EXISTS idx_finance_transactions_tenant ON finance_transactions;
DROP INDEX IF EXISTS idx_student_invoices_tenant ON student_invoices;
DROP INDEX IF EXISTS idx_expenses_tenant ON expenses;

-- Transport
DROP INDEX IF EXISTS idx_transport_tenant ON transport;
DROP INDEX IF EXISTS idx_transport_transactions_tenant ON transport_transactions;
DROP INDEX IF EXISTS idx_student_transport_tenant ON student_transport;

-- M-Pesa
DROP INDEX IF EXISTS idx_mpesa_stk_transaction_tenant ON mpesa_stk_transaction;
DROP INDEX IF EXISTS idx_mpesa_paybill_tenant ON mpesa_paybill;

-- Communications
DROP INDEX IF EXISTS idx_announcements_tenant ON announcements;
DROP INDEX IF EXISTS idx_messages_tenant ON messages;
DROP INDEX IF EXISTS idx_message_recipients_tenant ON message_recipients;
DROP INDEX IF EXISTS idx_attachments_tenant ON attachments;

-- Procurement
DROP INDEX IF EXISTS idx_purchase_orders_tenant ON purchase_orders;
DROP INDEX IF EXISTS idx_purchase_order_items_tenant ON purchase_order_items;
DROP INDEX IF EXISTS idx_delivery_notes_tenant ON delivery_notes;
DROP INDEX IF EXISTS idx_delivery_note_items_tenant ON delivery_note_items;
DROP INDEX IF EXISTS idx_supplier_invoices_tenant ON supplier_invoices;
DROP INDEX IF EXISTS idx_supplier_invoice_items_tenant ON supplier_invoice_items;
DROP INDEX IF EXISTS idx_supplier_payments_tenant ON supplier_payments;
DROP INDEX IF EXISTS idx_suppliers_tenant ON suppliers;
DROP INDEX IF EXISTS idx_ledger_entries_tenant ON ledger_entries;
DROP INDEX IF EXISTS idx_inventory_items_tenant ON inventory_items;
DROP INDEX IF EXISTS idx_inventory_transactions_tenant ON inventory_transactions;
DROP INDEX IF EXISTS idx_stock_requisitions_tenant ON stock_requisitions;
DROP INDEX IF EXISTS idx_stock_requisition_items_tenant ON stock_requisition_items;

-- Scoring
DROP INDEX IF EXISTS idx_exam_type_tenant ON exam_type;
DROP INDEX IF EXISTS idx_exam_type_grading_tenant ON exam_type_grading;
DROP INDEX IF EXISTS idx_students_score_tenant ON students_score;

-- Academics
DROP INDEX IF EXISTS idx_academic_subject_tenant ON academic_subject;
DROP INDEX IF EXISTS idx_class_subject_assignment_tenant ON class_subject_assignment;
DROP INDEX IF EXISTS idx_cbc_grade_result_tenant ON cbc_grade_result;

-- Audit
DROP INDEX IF EXISTS idx_audit_tenant ON audit;

-- ============================================================
-- 3. Restore original unique constraints (drop composite, add single-column)
-- ============================================================
DROP INDEX IF EXISTS idx_user_email_tenant ON user;
CREATE UNIQUE INDEX email ON user (email);

DROP INDEX IF EXISTS idx_role_name_tenant ON role;
CREATE UNIQUE INDEX name ON role (name);

-- ============================================================
-- 4. Drop tenant_id column from all tenant-scoped tables
-- ============================================================

-- Authentication
ALTER TABLE user DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE role DROP COLUMN IF EXISTS tenant_id;

-- Students
ALTER TABLE student DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE student_guardian DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE student_nemis DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE parent DROP COLUMN IF EXISTS tenant_id;

-- Grade
ALTER TABLE grade DROP COLUMN IF EXISTS tenant_id;

-- Fees
ALTER TABLE fee_structure DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE fee_components DROP COLUMN IF EXISTS tenant_id;

-- Finance
ALTER TABLE finance DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE finance_transactions DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE student_invoices DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE expenses DROP COLUMN IF EXISTS tenant_id;

-- Transport
ALTER TABLE transport DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE transport_transactions DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE student_transport DROP COLUMN IF EXISTS tenant_id;

-- M-Pesa
ALTER TABLE mpesa_stk_transaction DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE mpesa_paybill DROP COLUMN IF EXISTS tenant_id;

-- Communications
ALTER TABLE announcements DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE messages DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE message_recipients DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE attachments DROP COLUMN IF EXISTS tenant_id;

-- Procurement
ALTER TABLE purchase_orders DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE purchase_order_items DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE delivery_notes DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE delivery_note_items DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE supplier_invoices DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE supplier_invoice_items DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE supplier_payments DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE suppliers DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE ledger_entries DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE inventory_items DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE inventory_transactions DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE stock_requisitions DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE stock_requisition_items DROP COLUMN IF EXISTS tenant_id;

-- Scoring
ALTER TABLE exam_type DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE exam_type_grading DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE students_score DROP COLUMN IF EXISTS tenant_id;

-- Academics
ALTER TABLE academic_subject DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE class_subject_assignment DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE cbc_grade_result DROP COLUMN IF EXISTS tenant_id;

-- Audit
ALTER TABLE audit DROP COLUMN IF EXISTS tenant_id;

-- ============================================================
-- 5. Drop tenants table
-- ============================================================
DROP TABLE IF EXISTS tenants;
