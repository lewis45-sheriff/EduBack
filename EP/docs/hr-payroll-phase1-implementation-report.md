# HR & Payroll — Phase 1 Implementation Report

Status: Phase 1 implemented, built and unit-tested
Scope: Backend (Spring Boot, multi-tenant). Jurisdiction: Kenya (statutory figures are seeded defaults — confirm against current KRA/SHA/NSSF notices before go-live).
Build: `./mvnw -o clean package` → BUILD SUCCESS. HR tests: 37 passing (15 engine, 14 proration, 8 workflow).

This report distinguishes delivered Phase 1 functionality from deferred work and maps the result against the approved acceptance criteria.

---

## 1. What was built (Phase 1)

New module `com.EduePoa.EP.HR`, mirroring the existing module conventions (tenant-scoped entities, `CustomResponse<T>`, `@PreAuthorize("hasPermission(null, '<perm>')")`, `@Audit` + `AuditService`, Hibernate `ddl-auto=update` with a `SchemaUpdater` backfill where needed).

### 1.1 Permissions & segregation of duties
- Added to `Authentication/Enum/Permissions.java`: `payroll:process`, `payroll:approve`, `payroll:config`, `payroll:read`, `payroll:self:read`. `payroll:manage` already existed (description clarified).
- Role wiring uses the established pattern: `ROLE_ADMIN` is re-synced to the full enum on every boot (`CreateAdmin`), and new-tenant `School_Admin` receives all-except-`tenant:manage` (`TenantProvisioningService`). Teacher/parent/supplier/platform roles are curated and deliberately receive **no** payroll permission.
- `SchemaUpdater.backfillPayrollPermissions()` grants the five new permissions to **existing** tenants' `ROLE_ADMIN` and `School_Admin` rows (idempotent `NOT EXISTS` insert), since existing non-admin roles are never force-re-synced.
- Separation of duties is enforced in the backend (`PayrollServiceImpl.approveRun`) by comparing the run's `processedBy` to the authenticated approver resolved from the security context — never a client-supplied id. The processor cannot approve their own run.

### 1.2 Effective-dated statutory configuration (`HR/config`)
- Entities: `PayeBand`, `ReliefConfig`, `NssfConfig`, `ShifConfig`, `HousingLevyConfig`, `NitaConfig` — all tenant-scoped and effective-dated. Rates are **data, not code**.
- `StatutoryConfigService.resolveFor(asOf)` resolves the effective set into an immutable `StatutoryConfigSnapshot`, throwing a blocking `StatutoryConfigException` if any component is missing.
- `StatutoryConfigSeeder` (per-tenant, idempotent) seeds the 2024/2025-reform working defaults effective 2024-07-01 (SHIF 2.75% min 300, AHL 1.5%/1.5%, NSSF LEL 8,000/UEL 72,000 @ 6%/6%, PAYE bands 10/25/30/32.5/35%, personal relief 2,400, NITA 50).
- Admin surface `StatutoryConfigController` (`/api/v1/hr/statutory`): read the resolved config (`payroll:read`); create new effective-dated rows for each component (`payroll:config`). Writes always create a new effective-dated row — never mutate history.

### 1.3 Compensation management (`HR/compensation`)
- `EmployeeCompensation` (1:1 with the existing `Staff`), `Allowance` (taxable/pensionable/fixed-monthly flags), `RecurringDeduction` (pre/post-tax, active window).
- `CompensationController` (`/api/v1/hr/compensation`): write with `payroll:manage`; read with `payroll:read`/`payroll:manage`/`staff:read`.

### 1.4 Versioned, snapshotted proration policy (`HR/proration`)
- `ProrationPolicy` is versioned (monotonic per tenant) and effective-dated, immutable once created. It encodes every required policy dimension: trigger conditions (join/leave), start/end date inclusivity, calendar-vs-working-day counting (with a weekday mask), unpaid-day handling, the fixed-allowance rule, the rounding method/scale, and the effective date.
- `ProrationService.prorate(...)` is a pure day-counting function; `resolveSnapshot(...)` freezes the policy for a run. A documented built-in default (version 0) applies only when a tenant has configured no policy — it is explicit, not a silent assumption.
- `ProrationPolicyController` (`/api/v1/hr/proration-policy`): create a version (`payroll:config`); view versions/effective (`payroll:read`).
- Mid-period employment facts are captured per-run per-employee in `PayrollEmployeeAdjustment` (join/leave/unpaid-days), so recording a leaver or unpaid absence never modifies contractual salary history or any prior run.

### 1.5 Deterministic statutory engine (`HR/engine`)
- `PayrollCalculationService.compute(input, statutorySnapshot, prorationSnapshot)` is a pure, DB-free, deterministic calculator. It applies proration to eligible earnings first, then computes in the legally-ordered sequence: gross → NSSF (Tier I/II) → SHIF (with minimum) → AHL (employee + employer) → taxable income (statutory + pre-tax deductibles removed) → graduated PAYE → reliefs → net pay; employer contributions (NSSF employer, AHL employer, NITA) are tracked separately. All money is `BigDecimal` with the policy's rounding mode/scale. Negative net is surfaced as a blocking validation error, never silently saved.

### 1.6 Payroll workflow (`HR/payroll`)
- `PayrollRun` (one per tenant per month/year, DB-unique), `Payslip` (owns a `staff` reference for future self-service), `PayslipLine`, `PayrollEmployeeAdjustment`.
- Workflow: create (DRAFT) → process (PROCESSED) → approve (APPROVED) → mark paid (PAID); reject returns PROCESSED→DRAFT. Processing freezes the statutory and proration snapshots onto the run and recomputes payslips idempotently. Approval requires a PROCESSED run with no blocking errors and a different actor than the processor. APPROVED/PAID runs are frozen (cannot be reprocessed or edited). Optimistic locking (`@Version`) plus status guards protect against concurrent transitions. Every transition is audited.
- `PayrollController` (`/api/v1/hr/payroll`): create/process/adjustments/mark-paid (`payroll:process`); approve/reject (`payroll:approve`); reads (`payroll:read`).

### 1.7 Reconciliation & statutory summary (no Finance posting)
- `getStatutorySummary` returns PAYE, NSSF (employee/employer), SHIF, AHL (employee/employer), NITA and other deductions as **separate** figures, with per-body remittance totals, and a `reconciled` flag that re-sums the payslips and compares them to the stored run totals.
- Phase 1 records payment status (`markPaid`) **without** posting anything to the Finance/Expenses ledger. The run id, period and reconciled totals are preserved as the stable key for a future idempotent Phase 2 posting.

---

## 2. Acceptance criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Processing, approval and statutory-config permissions separately enforced | Met | `payroll:process` / `payroll:approve` / `payroll:config` on distinct endpoints |
| 2 | Same user cannot process and approve the same run | Met | `approveRun` SoD check vs authenticated actor; `PayrollServiceImplTest.processorCannotApprove` |
| 3 | Phase 1 supports monthly payroll only | Met | `PayrollRun` keyed by `{month, year}`, unique per tenant; no other frequency exists |
| 4 | Mid-period proration follows an approved, versioned policy | Met | `ProrationPolicy` (versioned, immutable) + snapshot frozen on each run; 14 proration tests |
| 5 | Payroll totals reconcile against all generated payslips | Met | `getStatutorySummary` reconciliation re-sums payslips vs stored totals |
| 6 | Phase 1 does not auto-post to the Finance/Expenses ledger | Met | `markPaid` records status only; no ledger write anywhere in the module |
| 7 | Self-service deferred to Phase 1.5 with identity/security preserved | Met | No self-service endpoint; `payroll:self:read` reserved, `Payslip.staff` ownership + `PayslipRepository.findByIdAndStaffId` + `StaffRepository.findByUserId` in place |
| 8 | Historical approved/paid records reproducible and immutable | Met | Frozen statutory + proration snapshots on the run; APPROVED/PAID cannot be reprocessed |
| 9 | Tenant isolation, authorization, proration, statutory, concurrency, audit tests pass | Partially automated (see §4) | 37 HR unit tests pass; isolation/authorization rely on the existing framework-level mechanisms |
| 10 | Report distinguishes completed vs deferred work | Met | This document |

---

## 3. Deferred (by design)

- **Phase 1.5 — Self-service & reporting:** staff "My Payslips" endpoint and page, payslip PDFs, payroll register / statutory schedule exports. The backend identity relationships, payslip ownership, and the `payroll:self:read` authorization boundary are already in place so this is additive.
- **Phase 2 — Finance & extended HR:** idempotent posting of payroll cost to the Finance/Expenses ledger with duplicate-posting protection and an auditable correction/reversal strategy; leave management. Phase 1 preserves the identifiers and reconciled totals this will consume.
- **Phase 3 — Advanced:** arrears/back pay, loan amortisation, SACCO schedules, year-end (P9) reporting, additional pay frequencies. The data model (effective-dated config, versioned policy, per-run adjustments) was designed to extend without disturbing historical records.

---

## 4. Verification performed and limitations

- `./mvnw -o clean package` compiles the entire backend including the new module: BUILD SUCCESS.
- 37 HR unit tests pass: engine statutory boundaries (NSSF tiers/UEL cap, SHIF minimum, PAYE band boundaries, reliefs, order-dependency, rounding, zero-gross, negative-net blocking, determinism), proration (first/middle/last-day joiner, mid-month leaver, 28- vs 31-day months, unpaid days, zero-eligible, invalid employment dates, inclusivity, trigger flags, working-day counting), and workflow (SoD, status-transition guards, blocking-approval gate, duplicate-run prevention).
- **Not independently re-tested here:** tenant isolation and `@PreAuthorize` authorization are enforced by the existing framework mechanisms (the Hibernate `tenantFilter` applied to every `TenantAwareRepository`, the `TenantEntityListener` write-stamping, and `CustomPermissionEvaluator`). All new entities extend `TenantScopedEntity` and all new repositories extend `TenantAwareRepository`, so they inherit the behaviour already covered by `TenantAwareTestBase` / `HibernateFilterInterceptorTest`. End-to-end `@SpringBootTest` coverage of a full create→process→approve cycle against a live datasource was not added (the DB schema is generated at boot via `ddl-auto=update`, which the test harness does not spin up here).
- **Compliance caveat:** the seeded statutory figures are 2024/2025 working defaults and **must be confirmed** against the latest KRA/SHA/NSSF/Housing Levy notices before production use. Because every rate is effective-dated configuration, a correction is an admin data edit (a new effective-dated row), not a redeploy, and it never alters an already-approved run.

---

## 5. Key API surface (all under `/api/v1/hr`, `CustomResponse<T>` envelope)

- `compensation` — set/read compensation, allowances, deductions.
- `statutory/config` (read), `statutory/{paye-bands,nssf,shif,housing-levy,nita,relief}` (write).
- `proration-policy` — create version, list versions, effective.
- `payroll/runs` — create, `{id}/process`, `{id}/approve`, `{id}/reject`, `{id}/mark-paid`, `{id}/adjustments`; `runs`, `runs/{id}`, `runs/{id}/payslips`, `payslips/{id}`, `runs/{id}/statutory-summary`.
