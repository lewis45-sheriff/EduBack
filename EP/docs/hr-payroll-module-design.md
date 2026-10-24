# HR & Payroll Module — Design & Implementation Plan

Status: Draft for review
Scope: Backend (Spring Boot, multi-tenant) + Frontend (Next.js teacher/admin portal)
Audience: Engineering, Finance/HR admins
Jurisdiction: Kenya (statutory deductions as of the 2024/2025 reforms)

> This document is a plan. Nothing here is implemented yet. It is written to fit the
> existing EduePoa architecture (multi-tenant `TenantScopedEntity`, `CustomResponse<T>`,
> permission-based `@PreAuthorize`, module layout `Controller/DTO/Entity/Enum/Repository/Service`).
> Figures and rates must be confirmed against current KRA/SHA/NSSF/Housing Levy law before go-live
> and are intended to be **configuration, not hard-coded constants**.

---

## 1. Objective

Build an HR & Payroll module that manages staff compensation and runs a compliant Kenyan payroll,
computing all **statutory deductions** per employee per pay period, producing payslips, and
generating the statutory remittance figures/returns the school must file.

The module must:

- Store each employee's compensation (basic salary, allowances, benefits).
- Run a monthly payroll per tenant (school), producing a payslip per employee.
- Compute statutory deductions correctly and in the legally-mandated order:
  - **NSSF** (pension) — Tier I & Tier II under the NSSF Act 2013.
  - **SHIF** (Social Health Insurance Fund) — replaces NHIF (2.75% of gross).
  - **Affordable Housing Levy (AHL)** — 1.5% of gross (employee) + 1.5% (employer).
  - **PAYE** (income tax) — graduated bands, with personal relief, insurance relief, and the
    statutory deductibility of NSSF/SHIF/AHL from taxable income.
  - **NITA** levy — employer-side training levy (KES 50/employee/month, remitted annually/periodically).
- Support employer contributions (NSSF employer, AHL employer, NITA) separately from employee deductions.
- Keep every rate, band, and threshold as **tenant-overridable configuration with national defaults**,
  so statutory changes are a data edit, not a code change.
- Produce payslips (PDF) and statutory summary reports (P10/PAYE, NSSF, SHIF, AHL schedules).
- Respect tenant isolation, existing permissions, and the `CustomResponse<T>` API envelope.

Non-goals (phase 1): bank integration/EFT disbursement, timesheet/clock-in, full leave accrual
engine, loans/SACCO third-party deductions beyond a generic "other deductions" line. These are
noted as future phases.

---

## 2. Statutory deductions — the rules to implement

> **Compliance note:** Kenyan payroll law changed materially in 2023–2024 (SHIF replaced NHIF,
> Housing Levy introduced, NSSF Act 2013 fully phased in, PAYE deductibility rules updated).
> The exact rates below are the working assumptions for the design; the implementation must read
> them from the `statutory_config` tables so they can be corrected without a redeploy. Treat the
> numbers here as defaults to seed, not as authoritative legal advice.

### 2.1 Computation order (critical)

Deductions are **order-dependent** because some reduce PAYE taxable income. The engine must compute
in this sequence:

1. **Gross pay** = basic salary + taxable allowances + taxable benefits.
2. **NSSF employee contribution** (Tier I + Tier II).
3. **SHIF employee contribution** (2.75% of gross).
4. **Affordable Housing Levy employee** (1.5% of gross).
5. **Taxable income** = gross − (NSSF + SHIF + AHL + any other allowable pre-tax deductions).
6. **PAYE (gross tax)** = apply graduated bands to taxable income.
7. **PAYE payable** = gross tax − personal relief − insurance relief − AHL relief (where applicable).
8. **Net pay** = gross − (NSSF + SHIF + AHL + PAYE + other post-tax deductions).
9. **Employer contributions** computed separately (NSSF employer, AHL employer, NITA) — not deducted
   from the employee, but tracked for remittance and employer cost.

The ordering, reliefs, and which items are pre-tax deductible are themselves configuration flags,
because they have changed with legislation.

### 2.2 NSSF (NSSF Act 2013, Tier I & II)

- Contribution = 6% of pensionable pay, matched by employer (6%), split across two tiers by
  Lower/Upper Earnings Limits.
- Tier I: on pensionable pay up to the Lower Earnings Limit (LEL).
- Tier II: on pensionable pay between LEL and Upper Earnings Limit (UEL).
- Config: `lowerEarningsLimit`, `upperEarningsLimit`, `employeeRate` (6%), `employerRate` (6%),
  effective-dated so the phased increases are data.

### 2.3 SHIF (Social Health Insurance Fund)

- Replaces NHIF. Employee contribution = **2.75% of gross salary**, subject to a configurable
  minimum (historically KES 300).
- Config: `rate` (2.75%), `minimumContribution`, effective date. No employer portion.

### 2.4 Affordable Housing Levy (AHL)

- Employee 1.5% of gross + employer 1.5% of gross.
- Employee portion is an allowable deduction / attracts relief per current rules — represented as a
  config flag (`preTaxDeductible` and/or `reliefRate`).
- Config: `employeeRate` (1.5%), `employerRate` (1.5%), relief treatment, effective date.

### 2.5 PAYE (income tax)

- Graduated monthly bands applied to taxable income. Working default bands (confirm before go-live):
  - 10% on the first KES 24,000
  - 25% on 24,001–32,333
  - 30% on 32,334–500,000
  - 32.5% on 500,001–800,000
  - 35% above 800,000
- **Personal relief**: KES 2,400/month.
- **Insurance relief**: 15% of premiums, capped (e.g. KES 5,000/month) — optional per employee.
- Reliefs and bands are fully table-driven and effective-dated.

### 2.6 NITA (training levy)

- Employer-side: KES 50 per employee per month (configurable), remitted on the employer's schedule.
- Tracked as an employer cost and in remittance reports, not deducted from the employee.

### 2.7 Other deductions

- Generic recurring or one-off deductions per employee (SACCO, loan, salary advance, welfare):
  configurable as pre-tax or post-tax, fixed amount or percentage, with optional start/end dates.

---

## 3. How this fits the existing system

- **Multi-tenancy:** every new entity extends `TenantScopedEntity` and carries the Hibernate
  `@Filter(name = "tenantFilter", ...)`. Each school's compensation, payroll runs, and config are
  isolated exactly like Students/Staff/Finance today. Statutory config has **national defaults**
  (seeded once) that a tenant can override.
- **Staff link:** compensation and payslips reference the existing `Staff` entity (`staff.id`). No
  duplication of employee identity. A new `EmployeeCompensation` 1:1 extends `Staff` with pay data.
- **Finance link:** a completed payroll run should post the total payroll cost (net pay + employer
  contributions) into the existing Finance/Expenses ledger as an expense entry, reusing current
  conventions rather than inventing a parallel ledger. (Phase 2 for the automatic posting; phase 1
  just exposes the totals.)
- **API conventions:** controllers return `CustomResponse<T>`, use `@PreAuthorize("hasPermission(null, '<perm>')")`,
  and the frontend reads the `{ message, statusCode, entity }` envelope via the existing apiClient.
- **Reports:** payslip and statutory-schedule PDFs reuse the existing JasperReports pipeline
  (`ReportsService`, `api/v1/reports-j`) the report cards already use.

---

## 4. Package & module layout

Mirror the existing module structure (as in `Budget`, `Staff`):

```
com.EduePoa.EP.HR
├── config            // statutory config entities + seeding (national defaults)
│   ├── entity        // PayeBand, NssfConfig, ShifConfig, HousingLevyConfig, NitaConfig, ReliefConfig
│   ├── repository
│   ├── service       // StatutoryConfigService (resolve effective config for a tenant+date)
│   └── seed          // StatutoryConfigSeeder (ApplicationRunner, per-tenant national defaults)
├── compensation
│   ├── entity        // EmployeeCompensation, Allowance, RecurringDeduction
│   ├── dto
│   ├── repository
│   ├── controller    // CompensationController  (/api/v1/hr/compensation)
│   └── service
├── payroll
│   ├── entity        // PayrollRun, Payslip, PayslipLine
│   ├── enums         // PayrollStatus (DRAFT, PROCESSED, APPROVED, PAID), DeductionType, LineType
│   ├── dto
│   ├── repository
│   ├── controller    // PayrollController  (/api/v1/hr/payroll)
│   └── service       // PayrollService + PayrollCalculationService (the statutory engine)
└── leave (phase 2)
    └── ...           // LeaveType, LeaveRequest, LeaveBalance
```

The **statutory engine** (`PayrollCalculationService`) is a pure, deterministic calculator that
takes gross pay + resolved config and returns a breakdown. It is unit-testable in isolation with no
DB, mirroring how `CbcCalculatorServiceImpl`/`AssessmentCalculationService` are tested.

---

## 5. Data model (entities)

All extend `TenantScopedEntity`. Money stored as `BigDecimal(precision=15, scale=2)`.

### 5.1 Configuration (effective-dated, tenant-overridable)

- **PayeBand**: `id`, `lowerBound`, `upperBound` (nullable = top band), `rate`, `sequence`,
  `effectiveFrom`, `effectiveTo`.
- **ReliefConfig**: `personalReliefMonthly`, `insuranceReliefRate`, `insuranceReliefCap`,
  `effectiveFrom/To`.
- **NssfConfig**: `lowerEarningsLimit`, `upperEarningsLimit`, `employeeRate`, `employerRate`,
  `effectiveFrom/To`.
- **ShifConfig**: `rate`, `minimumContribution`, `effectiveFrom/To`.
- **HousingLevyConfig**: `employeeRate`, `employerRate`, `preTaxDeductible` (bool), `reliefRate`,
  `effectiveFrom/To`.
- **NitaConfig**: `amountPerEmployee`, `effectiveFrom/To`.

A `StatutoryConfigService.resolveFor(tenant, asOfDate)` returns the single effective snapshot used by
a payroll run, so a run is reproducible even after rates change.

### 5.2 Compensation

- **EmployeeCompensation**: `staff` (OneToOne → `Staff`), `basicSalary`, `currency`,
  `paymentFrequency` (MONTHLY), `nssfPensionablePay` override (nullable), `insurancePremiumMonthly`
  (for relief), `active`, `effectiveFrom`.
- **Allowance**: `compensation`, `name`, `amount` (or `percentageOfBasic`), `taxable` (bool),
  `pensionable` (bool), `recurring` (bool).
- **RecurringDeduction**: `compensation`, `name`, `amount`/`percentage`, `preTax` (bool),
  `startDate`, `endDate` (nullable).

### 5.3 Payroll

- **PayrollRun**: `periodMonth`, `periodYear`, `status` (DRAFT/PROCESSED/APPROVED/PAID), `runBy`,
  `processedOn`, `configSnapshotRef`, totals (`totalGross`, `totalPaye`, `totalNssfEmployee`,
  `totalNssfEmployer`, `totalShif`, `totalAhlEmployee`, `totalAhlEmployer`, `totalNita`,
  `totalNet`, `employeeCount`). One run per tenant per month (unique constraint).
- **Payslip**: `payrollRun`, `staff`, `grossPay`, `taxableIncome`, `paye`, `nssfEmployee`,
  `nssfEmployer`, `shif`, `ahlEmployee`, `ahlEmployer`, `nita`, `otherDeductions`, `netPay`,
  `personalRelief`, `insuranceRelief`.
- **PayslipLine**: `payslip`, `lineType` (EARNING/STATUTORY/DEDUCTION/EMPLOYER_CONTRIB),
  `name`, `amount` — the itemised breakdown for display and audit.

---

## 6. API surface (phase 1)

Base: `/api/v1/hr`. All return `CustomResponse<T>`.

### Compensation
- `POST /compensation` — set/replace an employee's compensation. `@PreAuthorize("hasPermission(null,'payroll:manage')")`
- `GET  /compensation/staff/{staffId}` — read. `payroll:manage` or `staff:read`.
- `GET  /compensation` — list all (paged). `payroll:manage`.
- `POST /compensation/staff/{staffId}/allowances` / `.../deductions` — manage lines.

### Statutory config
- `GET  /statutory/config?asOf=YYYY-MM-DD` — resolved effective config. `payroll:manage`.
- `PUT  /statutory/paye-bands` / `/nssf` / `/shif` / `/housing-levy` / `/nita` / `/relief` — override
  tenant config (effective-dated). `payroll:manage` (consider a stricter `settings:manage`).

### Payroll
- `POST /payroll/runs` — create a DRAFT run for `{month, year}`. `payroll:manage`.
- `POST /payroll/runs/{id}/process` — compute all payslips (idempotent; re-processing a DRAFT
  recomputes). `payroll:manage`.
- `GET  /payroll/runs` — list runs. `payroll:manage`.
- `GET  /payroll/runs/{id}` — run header + totals. `payroll:manage`.
- `GET  /payroll/runs/{id}/payslips` — all payslips in the run. `payroll:manage`.
- `GET  /payroll/payslips/{id}` — one payslip with itemised lines. `payroll:manage`; a staff member
  may read **their own** via a self-service path (identity-based, not `payroll:manage`).
- `POST /payroll/runs/{id}/approve` — DRAFT/PROCESSED → APPROVED. `payroll:manage` (or a separate
  `payroll:approve` if segregation of duties is wanted).
- `GET  /payroll/runs/{id}/statutory-summary` — PAYE/NSSF/SHIF/AHL/NITA totals for remittance.

### Reports (reuse Jasper pipeline)
- `POST /reports-j/payslip` — payslip PDF for a payslip id.
- `POST /reports-j/payroll-statutory` — statutory schedule PDF for a run.

### Self-service (phase 1.5)
- `GET /hr/my-payslips` — the authenticated staff member's own payslips (identity-scoped, like
  `GET /api/v1/staff/my-assignment`).

---

## 7. Permissions

Reuse existing where possible:
- `payroll:manage` — compensation, run/process/approve payroll, edit statutory config.
- `staff:read` — read-only compensation views.
- `leave:manage` — phase 2 leave.

Optional new permissions (only if segregation of duties is required; add via role-seed migration and
`TeacherRolePermissionSyncRunner`-style backfill):
- `payroll:approve` — separate the approver from the processor.
- `payroll:config` — restrict statutory-rate edits to a narrower role than general payroll.

Self-service payslip read is authorized by **identity** (the caller is the payslip's staff), not by
`payroll:manage`, matching the existing `my-assignment`/teacher-portal pattern.

---

## 8. The statutory calculation engine (deterministic core)

`PayrollCalculationService.compute(input, config) -> PayslipBreakdown` where:

- `input`: gross components (basic, taxable/pensionable allowances), employee insurance premium,
  recurring deductions, NSSF pensionable override.
- `config`: the resolved effective snapshot (bands, rates, limits, reliefs).
- Returns the full breakdown: gross, NSSF (tier split), SHIF, AHL (employee+employer), taxable
  income, gross PAYE, reliefs, PAYE payable, NITA, other deductions, net pay, employer total cost.

Rules:
- Pure function, no DB, no tenant context — fully unit-testable.
- `BigDecimal` with `RoundingMode.HALF_UP` to 2 dp; document rounding at each statutory step
  (NSSF/SHIF/PAYE often round to the shilling — make rounding a config option).
- Never hard-code a rate or band; everything comes from `config`.
- Guard against negatives (PAYE payable floored at 0 after reliefs; net pay cannot go below 0 —
  surface a validation error if deductions exceed gross).

Worked example (defaults, gross = KES 100,000, confirm figures before go-live):
1. NSSF employee = 6% within tiers (e.g. ~KES 4,320 at current UEL).
2. SHIF = 2.75% × 100,000 = 2,750.
3. AHL employee = 1.5% × 100,000 = 1,500.
4. Taxable = 100,000 − 4,320 − 2,750 − 1,500 = 91,430.
5. Gross PAYE = banded tax on 91,430.
6. PAYE payable = gross PAYE − 2,400 personal relief − any insurance/AHL relief.
7. Net = 100,000 − (NSSF + SHIF + AHL + PAYE + other).

---

## 9. Validation & integrity rules

- One `PayrollRun` per tenant per `{month, year}` (DB unique constraint) — re-running recomputes the
  existing DRAFT rather than creating duplicates (idempotent, same pattern as the marks upsert).
- A run cannot be `process`ed twice after `APPROVED`/`PAID` — status transitions are guarded.
- Compensation changes after a run is APPROVED do not retroactively alter that run (the run holds its
  own computed payslips + config snapshot reference).
- Negative/zero net pay is a validation error, not a silent save.
- All money is `BigDecimal`; no floats anywhere in the engine.
- Every write is tenant-stamped by the existing `TenantEntityListener`; every read runs under the
  tenant filter.

---

## 10. Testing plan

Backend (JUnit + Mockito + AssertJ, matching existing tests):
1. Engine unit tests per statutory item (NSSF tiers at/below/above limits; SHIF minimum; AHL;
   PAYE at each band boundary; personal + insurance relief; combined worked examples).
2. Order-dependency test: confirm NSSF/SHIF/AHL reduce PAYE taxable income correctly.
3. Idempotent re-process test: processing a run twice yields identical payslips, no duplicates.
4. Rounding tests at shilling boundaries.
5. Tenant isolation: a run in tenant A never sees tenant B's compensation/config.
6. Authorization: `payroll:manage` required; self-service payslip only returns the caller's own.
7. Effective-dated config: a run in month X uses the config effective on that date even after rates
   later change.

Frontend:
- Payroll run list/detail rendering, payslip breakdown, statutory summary, PDF download, permission
  gating, loading/error/empty states.

Build verification: `mvnw compile` + targeted tests; frontend `tsc --noEmit` + `npm run build`.

---

## 11. Frontend (admin HR portal)

Under `src/app/eduapp/(admindashboard)/hr` (admin) — follows existing admin page conventions:
- **Compensation**: per-staff salary/allowances/deductions editor.
- **Statutory config**: effective-dated rate editors (PAYE bands, NSSF, SHIF, AHL, NITA, relief).
- **Payroll runs**: create run → process → review payslips → approve; totals dashboard.
- **Payslip view**: itemised earnings/statutory/deductions/employer breakdown; PDF download.
- **Statutory reports**: PAYE/NSSF/SHIF/AHL/NITA schedules per run, PDF export.
- A new API client `src/lib/api/hrApi.ts` using the shared `apiClient` + `ApiEnvelope`.
- Self-service: a staff "My Payslips" page reusing the identity-scoped endpoint.

Everything renders backend-computed figures only — no statutory math on the client.

---

## 12. Delivery phases

- **Phase 1 — Core payroll & statutory engine**
  Config entities + seeding, compensation, payroll run/process/approve, payslip, statutory summary,
  engine + unit tests, admin UI, payslip PDF.
- **Phase 1.5 — Self-service & reports**
  Staff "My Payslips", statutory schedule PDFs (P10/PAYE, NSSF, SHIF, AHL, NITA).
- **Phase 2 — Finance posting & leave**
  Auto-post payroll cost to Finance/Expenses ledger; leave types/requests/balances; NITA/annual
  returns; bank/EFT export (NACHA-style CSV or bank-specific).
- **Phase 3 — Advanced**
  Loans/advances with amortised recovery, SACCO third-party schedules, pro-rata for mid-month
  joiners/leavers, backpay/arrears, year-end P9 generation.

---

## 13. Open questions / confirm before implementation

1. **Statutory figures:** confirm current NSSF LEL/UEL, SHIF rate/minimum, AHL relief treatment, and
   PAYE bands/reliefs with the latest KRA/SHA notices — these are the seed defaults.
2. **Segregation of duties:** do you want a separate `payroll:approve` / `payroll:config` permission,
   or is `payroll:manage` sufficient for all actions?
3. **Pay frequency:** monthly only for phase 1, or also support weekly/fortnightly?
4. **Finance integration:** should an approved run auto-post to the Expenses ledger in phase 1, or is
   showing totals enough until phase 2?
5. **Currency:** KES only, or multi-currency?
6. **Mid-period joiners/leavers:** pro-rata required in phase 1, or full-month only initially?
7. **Self-service:** should staff see payslips in phase 1, or admin-only first?

Please review Sections 2 (rules), 6 (API), and 13 (open questions). Once the statutory figures and
the open questions are confirmed, I'll produce the implementation task breakdown and start with the
deterministic calculation engine and its unit tests.


---

## 14. Frontend Integration Prompt (HR & Payroll)

> Hand this section to a frontend engineer (or agent). It assumes the backend described above is
> live. It follows the existing EduePoa admin conventions: pages under
> `src/app/eduapp/(admindashboard)/<module>/page.tsx`, API clients under `src/lib/api/<name>Api.ts`
> using the shared `apiClient`, the `ApiEnvelope<T>` response shape, permission gating via
> `usePermissions`, nav registration in `src/lib/adminNavigation.tsx`, and the shared `@/components/ui`
> primitives (Table, Badge, Dialog, Select). All amounts are KES; render backend-computed figures
> only — never compute statutory math on the client.

### 14.1 Scope

Build the admin HR portal screens that consume the HR API:

1. **Compensation** — set/view each staff member's basic salary, allowances and recurring deductions.
2. **Statutory configuration** — view/edit the effective PAYE bands, NSSF, SHIF, Housing Levy, NITA
   and relief rates (effective-dated).
3. **Payroll runs** — create a run for a month/year, process it, review payslips, approve it, and see
   run totals + a statutory remittance summary.
4. **Payslip view** — itemised earnings / statutory deductions / other deductions / employer
   contributions, with PDF download.
5. (Phase 1.5) **My Payslips** self-service for staff.

### 14.2 API client (`src/lib/api/hrApi.ts`)

Create a single client, mirroring `budgetCategoryApi.ts`, with these groups (base `/api/v1/hr`):

- Compensation
  - `getCompensation(staffId)` → `GET /compensation/staff/{staffId}`
  - `listCompensation(params)` → `GET /compensation` (paged)
  - `saveCompensation(payload)` → `POST /compensation`
  - `addAllowance(staffId, payload)` / `addDeduction(staffId, payload)` → `POST /compensation/staff/{staffId}/allowances|deductions`
  - `deleteAllowance(id)` / `deleteDeduction(id)`
- Statutory config
  - `getStatutoryConfig(asOf?)` → `GET /statutory/config`
  - `updatePayeBands(payload)`, `updateNssf(payload)`, `updateShif(payload)`, `updateHousingLevy(payload)`, `updateNita(payload)`, `updateRelief(payload)`
- Payroll
  - `createRun({month, year})` → `POST /payroll/runs`
  - `processRun(id)` → `POST /payroll/runs/{id}/process`
  - `approveRun(id)` → `POST /payroll/runs/{id}/approve`
  - `listRuns(params)` → `GET /payroll/runs`
  - `getRun(id)` → `GET /payroll/runs/{id}`
  - `getRunPayslips(id)` → `GET /payroll/runs/{id}/payslips`
  - `getPayslip(id)` → `GET /payroll/payslips/{id}`
  - `getStatutorySummary(id)` → `GET /payroll/runs/{id}/statutory-summary`
  - `downloadPayslip(payslipId)` → `apiClient.downloadPost("/api/v1/reports-j/payslip", { payslipId })`

Every call reads the `ApiEnvelope<T>` off `res.data.entity`; surface `res.error` on failure. Mutations
rely on the apiClient's default success/error toasts unless a custom message is needed.

### 14.3 TypeScript types (`src/types/hr.ts`)

Mirror the backend DTOs exactly (no `any`): `EmployeeCompensation`, `Allowance`, `RecurringDeduction`,
`PayeBand`, `NssfConfig`, `ShifConfig`, `HousingLevyConfig`, `NitaConfig`, `ReliefConfig`,
`StatutoryConfigSnapshot`, `PayrollRun`, `PayrollStatus` (`DRAFT|PROCESSED|APPROVED|PAID`), `Payslip`,
`PayslipLine` (`lineType: "EARNING"|"STATUTORY"|"DEDUCTION"|"EMPLOYER_CONTRIB"`), and the statutory
summary shape. Re-export `ApiEnvelope` from `@/types/procurement`.

### 14.4 Pages & routes

- `/eduapp/hr/compensation` — staff list with salary; row opens an editor (basic salary, allowances,
  recurring deductions). Gated `payroll:manage` (read allowed with `staff:read`).
- `/eduapp/hr/statutory` — tabbed effective-config editors (PAYE bands table, NSSF, SHIF, Housing
  Levy, NITA, relief). Gated `payroll:manage`.
- `/eduapp/hr/payroll` — list of payroll runs with status badges; "New run" (pick month/year);
  actions Process / Approve per run status; a totals strip. Gated `payroll:manage`.
- `/eduapp/hr/payroll/[runId]` — run detail: totals, statutory summary, payslip table; each row opens
  the payslip; PDF download.
- (Phase 1.5) `/teacher/payslips` or a staff self-service page — the caller's own payslips.

Each page follows the Budget-category page shell: a `min-h-screen bg-muted p-4 md:p-6` wrapper, a
header card with an icon + title + description, then the content, with loading/error/empty states
using the shared Table `TableEmpty` variants.

### 14.5 Status & money presentation

- Status badges reuse the existing `Badge` variants: DRAFT = secondary, PROCESSED = default,
  APPROVED = default (success tone), PAID = outline.
- Money: format with `Intl.NumberFormat("en-KE", { style: "currency", currency: "KES" })` in a small
  shared helper; right-align numeric columns.
- The payslip view groups lines by `lineType`: Earnings, Statutory Deductions (PAYE, NSSF, SHIF, AHL),
  Other Deductions, Employer Contributions (NSSF, AHL, NITA), then Net Pay as a bold footer.

### 14.6 Navigation (`src/lib/adminNavigation.tsx`)

Add a top-level **Human Resources** section (icon `Wallet`/`UserCog`/`BadgeDollarSign`), gated on the
union of its children's permissions, with sub-items:
- Compensation → `/eduapp/hr/compensation` (`payroll:manage`)
- Payroll → `/eduapp/hr/payroll` (`payroll:manage`)
- Statutory Config → `/eduapp/hr/statutory` (`payroll:manage`)

(Keep "Staff Management" where it is; HR is the pay/statutory layer on top of staff.)

### 14.7 Rules & guardrails

- Never recompute PAYE/NSSF/SHIF/AHL on the client — display backend values.
- Disable Process/Approve when a run's status does not allow the transition; don't rely on the button
  alone (the backend also enforces it).
- Prevent duplicate run creation for an existing month/year (surface the backend conflict message).
- Preserve unsaved compensation edits on a failed save; show the backend validation message.
- Permission-gate every page and every nav item; hiding UI is not a substitute for the backend
  authorization already enforced by `@PreAuthorize`.
- Verify with `npx tsc --noEmit` and `npm run build` before considering the work done.

### 14.8 Definition of done (frontend)

- `hrApi.ts` + `types/hr.ts` match the implemented endpoints and DTOs; no dead calls.
- Compensation, statutory config, payroll run list/detail, and payslip pages work end-to-end against
  the running backend.
- A payroll run can be created → processed → approved, payslips render with the correct statutory
  breakdown, and the payslip PDF downloads.
- Navigation is permission-aware; no dead routes.
- `tsc --noEmit` and the production build pass.
