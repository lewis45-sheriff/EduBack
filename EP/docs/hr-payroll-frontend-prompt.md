# HR & Payroll (Admin Portal) — Front-End Integration Prompt

This document hands the **EduePoa HR & Payroll Phase 1** frontend to an engineer/agent working in
the Next.js app at `SchoolManagement/schoolapp`. The backend is implemented and tested; this spec
describes exactly what to build against it.

It follows the app's existing conventions: API clients under `src/lib/api/<name>Api.ts` using the
shared `apiClient`, the `ApiEnvelope<T>` / `SpringPage<T>` / `SortDir` shapes from
`@/types/procurement`, permission gating via `usePermissions()` (`src/lib/hooks/usePermissions.ts`),
nav registration in `src/lib/adminNavigation.tsx`, admin pages under
`src/app/eduapp/(admindashboard)/<module>/page.tsx`, and the shared `@/components/ui` primitives.

> **Scope.** Phase 1 = the **admin** HR portal (compensation, statutory config, proration policy,
> payroll runs, payslip view). Staff self-service ("My Payslips") and payslip/register PDFs are
> **Phase 1.5** and are explicitly out of scope here. Never compute statutory or proration math on
> the client — always render backend-computed figures.

---

## 1. Permissions

The backend enforces **segregation of duties** with separate permissions. Mirror them in the UI
(gate pages, nav items, and action buttons), remembering the backend is the real gate — hiding a
button is not a substitute.

| Permission string | What it allows |
|-------------------|----------------|
| `payroll:manage` | Manage compensation (salary, allowances, recurring deductions) |
| `payroll:process` | Create a run, process/recompute it, set employment adjustments, mark paid |
| `payroll:approve` | Approve or reject a processed run |
| `payroll:config` | Create/modify statutory configuration and the proration policy |
| `payroll:read` | Read payroll runs, payslips, statutory summary, config, policy |
| `payroll:self:read` | (Phase 1.5) a staff member reads their own payslips — not used in Phase 1 |

Key rule to reflect in the UI: **the user who processed a run cannot approve it.** The backend
returns HTTP 409 with a clear message if they try. Prefer to disable/hide the **Approve** action
on a run whose `processedByName` is the current user, and always surface the backend 409 message if
it still happens.

Use `usePermissions()`:
```ts
const { hasPermission, hasAnyPermission } = usePermissions();
hasPermission("payroll:process");
hasAnyPermission(["payroll:read", "payroll:process", "payroll:approve"]);
```

---

## 2. API surface

Base path: `/api/v1/hr`. Every response uses the standard envelope
`{ message: string, statusCode: number, entity: <data|null> }` (`ApiEnvelope<T>`). Read `entity`
off `res.data`; surface `res.error` on failure (the `apiClient` already toasts errors/success).

### 2.1 Compensation — `/api/v1/hr/compensation`

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `POST /compensation` | `payroll:manage` | `CompensationRequest` | Create/replace a staff member's compensation (replace-semantic for allowance/deduction lines) |
| `GET /compensation/staff/{staffId}` | `payroll:read` \| `payroll:manage` \| `staff:read` | — | One staff member's compensation |
| `GET /compensation?page&size` | `payroll:read` \| `payroll:manage` | — | Paged list (envelope `entity` = `{ content, currentPage, totalItems, totalPages }`) |
| `POST /compensation/staff/{staffId}/allowances` | `payroll:manage` | `AllowanceRequest` | Add one allowance |
| `DELETE /compensation/staff/{staffId}/allowances/{allowanceId}` | `payroll:manage` | — | Remove an allowance |
| `POST /compensation/staff/{staffId}/deductions` | `payroll:manage` | `RecurringDeductionRequest` | Add one recurring deduction |
| `DELETE /compensation/staff/{staffId}/deductions/{deductionId}` | `payroll:manage` | — | Remove a recurring deduction |

### 2.2 Statutory config — `/api/v1/hr/statutory`

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `GET /statutory/config?asOf=YYYY-MM-DD` | `payroll:read` \| `payroll:config` \| `payroll:manage` | — | Resolved effective config snapshot for a date (defaults to the current month) |
| `PUT /statutory/paye-bands` | `payroll:config` | `PayeBandsUpdateRequest` | New effective-dated PAYE band schedule |
| `PUT /statutory/nssf` | `payroll:config` | `NssfConfigRequest` | New effective-dated NSSF config |
| `PUT /statutory/shif` | `payroll:config` | `ShifConfigRequest` | New effective-dated SHIF config |
| `PUT /statutory/housing-levy` | `payroll:config` | `HousingLevyConfigRequest` | New effective-dated AHL config |
| `PUT /statutory/nita` | `payroll:config` | `NitaConfigRequest` | New effective-dated NITA config |
| `PUT /statutory/relief` | `payroll:config` | `ReliefConfigRequest` | New effective-dated relief config |

Each `PUT` **creates a new effective-dated row**; it does not edit an existing one. Submitting an
`effectiveFrom` that already has a row returns HTTP 409 (choose a different date to supersede).
Rates are fractions (e.g. `0.0275` = 2.75%, `0.06` = 6%).

### 2.3 Proration policy — `/api/v1/hr/proration-policy`

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `POST /proration-policy` | `payroll:config` | `ProrationPolicyRequest` | Create a new **immutable, versioned** policy (server assigns `version = max + 1`) |
| `GET /proration-policy/versions` | `payroll:read` \| `payroll:config` \| `payroll:manage` | — | All versions (newest first), `entity` = array |
| `GET /proration-policy/effective?asOf=YYYY-MM-DD` | `payroll:read` \| `payroll:config` \| `payroll:manage` | — | The policy in force on a date; `entity` is `null` when none is stored (the system default applies) |

### 2.4 Payroll runs — `/api/v1/hr/payroll`

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `POST /payroll/runs` | `payroll:process` | `CreateRunRequest` `{ month, year }` | Create a DRAFT run (409 if one already exists for that month/year) |
| `POST /payroll/runs/{id}/process` | `payroll:process` | — | Compute all payslips (idempotent recompute). Freezes config + proration snapshots on the run. 422 if statutory config is missing |
| `POST /payroll/runs/{id}/adjustments` | `payroll:process` | `EmployeeAdjustmentRequest` | Upsert a mid-period join/leave/unpaid-days adjustment (DRAFT/PROCESSED only). Reprocess to apply |
| `GET /payroll/runs/{id}/adjustments` | `payroll:read` \| `payroll:process` | — | List adjustments for a run |
| `POST /payroll/runs/{id}/approve` | `payroll:approve` | `ApproveRunRequest` `{ comment? }` | PROCESSED → APPROVED. 409 if the approver processed it, or if blocking errors exist |
| `POST /payroll/runs/{id}/reject?reason=...` | `payroll:approve` | — | PROCESSED → DRAFT |
| `POST /payroll/runs/{id}/mark-paid` | `payroll:process` | `MarkPaidRequest` `{ paymentReference? }` | APPROVED → PAID (records status only; **no Finance posting in Phase 1**) |
| `GET /payroll/runs?status&page&size` | `payroll:read` \| `payroll:process` \| `payroll:approve` | — | Paged runs, optional `status` filter. `entity` = `{ content, currentPage, totalItems, totalPages }` |
| `GET /payroll/runs/{id}` | read/process/approve | — | Run header + totals |
| `GET /payroll/runs/{id}/payslips` | read/process/approve | — | All payslips in the run (`entity` = array) |
| `GET /payroll/payslips/{id}` | read/process/approve | — | One payslip with itemised `lines` |
| `GET /payroll/runs/{id}/statutory-summary` | read/process/approve | — | Reconciled remittance summary (employee vs employer figures + `reconciled` flag) |

---

## 3. TypeScript types — `src/types/hr.ts`

Create a `hr.ts` types file, re-exporting the shared envelope/page types (mirroring how
`src/types/budget.ts` re-exports from `@/types/procurement`). All amounts are KES. Names mirror the
backend DTOs exactly — no `any`.

```ts
import type { ApiEnvelope, SortDir, SpringPage } from "@/types/procurement";
export type { ApiEnvelope, SortDir, SpringPage };

// ---- enums ----
export type AmountBasis = "FIXED" | "PERCENTAGE_OF_BASIC";
export type PayrollStatus = "DRAFT" | "PROCESSED" | "APPROVED" | "PAID";
export type PayslipLineType = "EARNING" | "STATUTORY" | "DEDUCTION" | "EMPLOYER_CONTRIB";
export type DayCountMethod = "CALENDAR_DAYS" | "WORKING_DAYS";
export type FixedAllowanceProrationRule = "PRORATE" | "PAY_IN_FULL";
export type ProrationRounding = "HALF_UP" | "HALF_EVEN" | "DOWN" | "UP";

// ---- compensation ----
export interface Allowance {
  id: number; name: string; basis: AmountBasis; amount: number;
  taxable: boolean; pensionable: boolean; fixedMonthly: boolean;
}
export interface RecurringDeduction {
  id: number; name: string; basis: AmountBasis; amount: number;
  preTax: boolean; startDate?: string | null; endDate?: string | null;
}
export interface EmployeeCompensation {
  id: number; staffId: number; staffName?: string | null; employeeNumber?: string | null;
  basicSalary: number; currency: string;
  nssfPensionablePayOverride?: number | null; insurancePremiumMonthly?: number | null;
  active: boolean; effectiveFrom?: string | null;
  allowances: Allowance[]; recurringDeductions: RecurringDeduction[];
}
export interface AllowanceRequest {
  name: string; basis: AmountBasis; amount: number;
  taxable: boolean; pensionable: boolean; fixedMonthly: boolean;
}
export interface RecurringDeductionRequest {
  name: string; basis: AmountBasis; amount: number;
  preTax: boolean; startDate?: string | null; endDate?: string | null;
}
export interface CompensationRequest {
  staffId: number; basicSalary: number; currency?: string;
  nssfPensionablePayOverride?: number | null; insurancePremiumMonthly?: number | null;
  active?: boolean; effectiveFrom?: string | null;
  allowances?: AllowanceRequest[]; recurringDeductions?: RecurringDeductionRequest[];
}

// ---- statutory config (snapshot returned by GET /statutory/config) ----
export interface PayeBandSnapshot { sequence: number; lowerBound: number; upperBound: number | null; rate: number; }
export interface StatutoryConfigSnapshot {
  asOf: string;
  payeBands: PayeBandSnapshot[];
  personalReliefMonthly: number; insuranceReliefRate: number; insuranceReliefCap: number;
  nssfLowerEarningsLimit: number; nssfUpperEarningsLimit: number;
  nssfEmployeeRate: number; nssfEmployerRate: number;
  shifRate: number; shifMinimumContribution: number;
  ahlEmployeeRate: number; ahlEmployerRate: number;
  ahlPreTaxDeductible: boolean; ahlReliefRate: number;
  nitaAmountPerEmployee: number;
}
export interface PayeBandRequest { sequence: number; lowerBound: number; upperBound?: number | null; rate: number; }
export interface PayeBandsUpdateRequest { effectiveFrom: string; bands: PayeBandRequest[]; }
export interface NssfConfigRequest { effectiveFrom: string; lowerEarningsLimit: number; upperEarningsLimit: number; employeeRate: number; employerRate: number; }
export interface ShifConfigRequest { effectiveFrom: string; rate: number; minimumContribution: number; }
export interface HousingLevyConfigRequest { effectiveFrom: string; employeeRate: number; employerRate: number; preTaxDeductible: boolean; reliefRate?: number; }
export interface NitaConfigRequest { effectiveFrom: string; amountPerEmployee: number; }
export interface ReliefConfigRequest { effectiveFrom: string; personalReliefMonthly: number; insuranceReliefRate: number; insuranceReliefCap: number; }

// ---- proration policy ----
export interface ProrationPolicy {
  id: number; version: number; effectiveFrom: string; description?: string | null;
  prorateOnJoin: boolean; prorateOnLeave: boolean;
  startDateInclusive: boolean; endDateInclusive: boolean;
  dayCountMethod: DayCountMethod; workingDaysMask: string;
  unpaidDaysSupported: boolean; fixedAllowanceRule: FixedAllowanceProrationRule;
  rounding: ProrationRounding; roundingScale: number;
  createdAt?: string | null; createdBy?: string | null;
}
export interface ProrationPolicyRequest {
  effectiveFrom: string; description?: string;
  prorateOnJoin: boolean; prorateOnLeave: boolean;
  startDateInclusive: boolean; endDateInclusive: boolean;
  dayCountMethod: DayCountMethod; workingDaysMask: string;
  unpaidDaysSupported: boolean; fixedAllowanceRule: FixedAllowanceProrationRule;
  rounding: ProrationRounding; roundingScale: number;
}

// ---- payroll ----
export interface CreateRunRequest { month: number; year: number; }
export interface ApproveRunRequest { comment?: string; }
export interface MarkPaidRequest { paymentReference?: string; }
export interface EmployeeAdjustmentRequest {
  staffId: number; joinDate?: string | null; leaveDate?: string | null; unpaidDays: number;
}
export interface PayrollRun {
  id: number; periodMonth: number; periodYear: number; status: PayrollStatus;
  totalGross: number; totalPaye: number;
  totalNssfEmployee: number; totalNssfEmployer: number;
  totalShif: number; totalAhlEmployee: number; totalAhlEmployer: number;
  totalNita: number; totalOtherDeductions: number;
  totalEmployeeDeductions: number; totalEmployerContributions: number;
  totalNet: number; totalEmployerCost: number;
  employeeCount: number; hasBlockingErrors: boolean; prorationPolicyVersion?: number | null;
  processedByName?: string | null; processedAt?: string | null;
  approvedByName?: string | null; approvedAt?: string | null; approvalComment?: string | null;
  paidAt?: string | null; paymentReference?: string | null; createdAt?: string | null;
  warnings?: string[];
}
export interface PayslipLine { lineType: PayslipLineType; name: string; amount: number; }
export interface Payslip {
  id: number; payrollRunId: number; periodMonth: number; periodYear: number;
  staffId: number; staffName?: string | null; employeeNumber?: string | null;
  basicPay: number; grossPay: number;
  paye: number; nssfEmployee: number; shif: number; ahlEmployee: number; otherDeductions: number;
  taxableIncome: number; personalRelief: number; insuranceRelief: number;
  totalEmployeeDeductions: number; netPay: number;
  nssfEmployer: number; ahlEmployer: number; nita: number; totalEmployerContributions: number;
  prorated: boolean; eligibleDays?: number | null; totalDays?: number | null;
  lines: PayslipLine[];
}
export interface StatutorySummary {
  payrollRunId: number; periodMonth: number; periodYear: number; employeeCount: number;
  paye: number; nssfEmployee: number; shif: number; ahlEmployee: number;
  otherDeductions: number; totalEmployeeDeductions: number;
  nssfEmployer: number; ahlEmployer: number; nita: number; totalEmployerContributions: number;
  nssfTotalRemittance: number; ahlTotalRemittance: number;
  totalGross: number; totalNet: number; totalEmployerCost: number;
  reconciled: boolean; reconciliationNote: string;
}
export interface PagedRuns { content: PayrollRun[]; currentPage: number; totalItems: number; totalPages: number; }
export interface PagedCompensation { content: EmployeeCompensation[]; currentPage: number; totalItems: number; totalPages: number; }
```

---

## 4. API client — `src/lib/api/hrApi.ts`

Create one client mirroring `budgetCategoryApi.ts` (import `apiClient`, type each call with the
`ApiEnvelope<T>` of its `entity`). Suggested shape:

```ts
import { apiClient } from "@/lib/apiClient";
import type {
  ApiEnvelope, CompensationRequest, EmployeeCompensation, PagedCompensation, PagedRuns,
  AllowanceRequest, RecurringDeductionRequest, StatutoryConfigSnapshot,
  PayeBandsUpdateRequest, NssfConfigRequest, ShifConfigRequest, HousingLevyConfigRequest,
  NitaConfigRequest, ReliefConfigRequest, ProrationPolicy, ProrationPolicyRequest,
  CreateRunRequest, ApproveRunRequest, MarkPaidRequest, EmployeeAdjustmentRequest,
  PayrollRun, Payslip, StatutorySummary, PayrollStatus,
} from "@/types/hr";

const HR = "/api/v1/hr";

export const hrApi = {
  // compensation
  getCompensation: (staffId: number) =>
    apiClient.get<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation/staff/${staffId}`, { showErrorToast: false }),
  listCompensation: (page = 0, size = 10) =>
    apiClient.get<ApiEnvelope<PagedCompensation>>(`${HR}/compensation?page=${page}&size=${size}`),
  saveCompensation: (body: CompensationRequest) =>
    apiClient.post<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation`, body),
  addAllowance: (staffId: number, body: AllowanceRequest) =>
    apiClient.post<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation/staff/${staffId}/allowances`, body),
  deleteAllowance: (staffId: number, id: number) =>
    apiClient.delete<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation/staff/${staffId}/allowances/${id}`),
  addDeduction: (staffId: number, body: RecurringDeductionRequest) =>
    apiClient.post<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation/staff/${staffId}/deductions`, body),
  deleteDeduction: (staffId: number, id: number) =>
    apiClient.delete<ApiEnvelope<EmployeeCompensation>>(`${HR}/compensation/staff/${staffId}/deductions/${id}`),

  // statutory config
  getStatutoryConfig: (asOf?: string) =>
    apiClient.get<ApiEnvelope<StatutoryConfigSnapshot>>(`${HR}/statutory/config${asOf ? `?asOf=${asOf}` : ""}`, { showErrorToast: false }),
  updatePayeBands: (body: PayeBandsUpdateRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/paye-bands`, body),
  updateNssf: (body: NssfConfigRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/nssf`, body),
  updateShif: (body: ShifConfigRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/shif`, body),
  updateHousingLevy: (body: HousingLevyConfigRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/housing-levy`, body),
  updateNita: (body: NitaConfigRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/nita`, body),
  updateRelief: (body: ReliefConfigRequest) => apiClient.put<ApiEnvelope<null>>(`${HR}/statutory/relief`, body),

  // proration policy
  listProrationVersions: () => apiClient.get<ApiEnvelope<ProrationPolicy[]>>(`${HR}/proration-policy/versions`),
  getEffectiveProration: (asOf?: string) =>
    apiClient.get<ApiEnvelope<ProrationPolicy | null>>(`${HR}/proration-policy/effective${asOf ? `?asOf=${asOf}` : ""}`, { showErrorToast: false }),
  createProrationVersion: (body: ProrationPolicyRequest) =>
    apiClient.post<ApiEnvelope<ProrationPolicy>>(`${HR}/proration-policy`, body),

  // payroll
  createRun: (body: CreateRunRequest) => apiClient.post<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs`, body),
  processRun: (id: number) => apiClient.post<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs/${id}/process`, {}),
  approveRun: (id: number, body: ApproveRunRequest) => apiClient.post<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs/${id}/approve`, body),
  rejectRun: (id: number, reason?: string) =>
    apiClient.post<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs/${id}/reject${reason ? `?reason=${encodeURIComponent(reason)}` : ""}`, {}),
  markPaid: (id: number, body: MarkPaidRequest) => apiClient.post<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs/${id}/mark-paid`, body),
  setAdjustment: (id: number, body: EmployeeAdjustmentRequest) =>
    apiClient.post<ApiEnvelope<unknown>>(`${HR}/payroll/runs/${id}/adjustments`, body),
  listAdjustments: (id: number) => apiClient.get<ApiEnvelope<EmployeeAdjustmentRequest[]>>(`${HR}/payroll/runs/${id}/adjustments`),
  listRuns: (status?: PayrollStatus, page = 0, size = 12) => {
    const q = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) q.set("status", status);
    return apiClient.get<ApiEnvelope<PagedRuns>>(`${HR}/payroll/runs?${q.toString()}`);
  },
  getRun: (id: number) => apiClient.get<ApiEnvelope<PayrollRun>>(`${HR}/payroll/runs/${id}`),
  getRunPayslips: (id: number) => apiClient.get<ApiEnvelope<Payslip[]>>(`${HR}/payroll/runs/${id}/payslips`),
  getPayslip: (id: number) => apiClient.get<ApiEnvelope<Payslip>>(`${HR}/payroll/payslips/${id}`),
  getStatutorySummary: (id: number) => apiClient.get<ApiEnvelope<StatutorySummary>>(`${HR}/payroll/runs/${id}/statutory-summary`),
};
```

Read `res.data?.entity` for data and `res.data?.message` / `res.error` for messaging. Mutations
rely on `apiClient`'s default success/error toasts.

---

## 5. Pages & routes

All under `src/app/eduapp/(admindashboard)/hr/...`, each following the existing admin page shell
(a `min-h-screen bg-muted p-4 md:p-6` wrapper, a header card with an icon + title + description,
then content, with loading / error / empty states). Gate each page with `usePermissions()` and
render an access-denied state when the user lacks the permission.

1. **`/eduapp/hr/compensation`** — gated `payroll:read` (edit needs `payroll:manage`).
   Staff list with basic salary; a row opens an editor dialog (basic salary, currency,
   NSSF pensionable override, insurance premium, allowances table, recurring deductions table).
   Saving uses `saveCompensation` (full replace). Preserve unsaved edits on a failed save and show
   the backend validation message. Pull the staff list from the existing staff API
   (`src/lib/api/staffApi.ts`); compensation is keyed by `staffId`.

2. **`/eduapp/hr/statutory`** — gated `payroll:read` (edit needs `payroll:config`).
   Tabbed effective-config viewer/editor. Load the resolved snapshot with `getStatutoryConfig`.
   Tabs: PAYE bands (editable table), NSSF, SHIF, Housing Levy, NITA, Relief. Each "Save" collects
   an `effectiveFrom` plus the fields and calls the matching `PUT`. Show rates as percentages in
   the UI but send fractions to the API (e.g. display `2.75%`, send `0.0275`). Surface the 409
   "already exists effective <date>" message.

3. **`/eduapp/hr/proration-policy`** — gated `payroll:read` (create needs `payroll:config`).
   Show the effective policy and the version history (newest first). A "New version" form captures
   all policy fields (triggers, inclusivity, day-count method + working-days mask, unpaid-days
   support, fixed-allowance rule, rounding + scale, effectiveFrom). Policies are immutable — there
   is no edit, only create-a-new-version. If `getEffectiveProration` returns `entity: null`, show
   "System default applies (calendar days, inclusive dates, fixed allowances prorated, HALF_UP)".

4. **`/eduapp/hr/payroll`** — gated `payroll:read` (actions need process/approve).
   Runs list with status badges and a totals strip. "New run" (month/year picker) → `createRun`.
   Per-row actions depend on status:
   - DRAFT: **Process** (`payroll:process`)
   - PROCESSED: **Approve** / **Reject** (`payroll:approve`), **Reprocess** (`payroll:process`)
   - APPROVED: **Mark paid** (`payroll:process`)
   - PAID: read-only
   Disable an action when the status does not allow the transition. Disable **Approve** when
   `run.processedByName` equals the current user (SoD), and when `run.hasBlockingErrors` is true.
   Surface backend 409 messages (duplicate run, SoD, blocking errors) via the toast.

5. **`/eduapp/hr/payroll/[runId]`** — gated `payroll:read`.
   Run detail: totals, the statutory summary (from `getStatutorySummary`, with a reconciled
   badge from `reconciled`), the payslip table, and the employment-adjustments panel
   (`payroll:process` to add a join/leave/unpaid-days adjustment, then **Reprocess**). If
   `run.warnings` is non-empty after processing, show them prominently (these flag zero-eligible
   employees, floored net, invalid employment dates). Each payslip row opens the payslip view.

6. **Payslip view** (dialog or `/eduapp/hr/payroll/[runId]/payslips/[payslipId]`) — gated
   `payroll:read`. Group `lines` by `lineType`: **Earnings**, **Statutory Deductions**
   (PAYE, NSSF, SHIF, AHL), **Other Deductions**, **Employer Contributions** (NSSF, AHL, NITA),
   then **Net Pay** as a bold footer. Show proration info (`prorated`, `eligibleDays/totalDays`)
   when `prorated` is true. (PDF download is Phase 1.5 — leave a disabled/"coming soon" affordance.)

---

## 6. Status & money presentation

- **Status badges** (reuse the existing `Badge` variants): DRAFT = secondary, PROCESSED = default,
  APPROVED = success tone (default), PAID = outline. If `hasBlockingErrors` on a PROCESSED run,
  add a destructive "Errors" badge.
- **Money**: a shared helper
  `new Intl.NumberFormat("en-KE", { style: "currency", currency: "KES" }).format(n)`;
  right-align numeric columns.
- **Rates**: display as percentages (`value * 100` with up to 4 dp), send fractions.

---

## 7. Navigation — `src/lib/adminNavigation.tsx`

Add a top-level **Human Resources** section (icon from `lucide-react`, e.g. `Wallet`/`BadgeDollarSign`),
gated on the union of its children's permissions, with sub-items:

```tsx
{
  title: "Human Resources",
  icon: <Wallet className="h-5 w-5" />,
  permissions: ["payroll:read", "payroll:manage", "payroll:process", "payroll:approve", "payroll:config"],
  subItems: [
    { title: "Compensation",     path: "/eduapp/hr/compensation",      icon: <UserCog className="h-4 w-4" />,       permissions: ["payroll:manage", "payroll:read"] },
    { title: "Payroll Runs",     path: "/eduapp/hr/payroll",           icon: <Calculator className="h-4 w-4" />,    permissions: ["payroll:read", "payroll:process", "payroll:approve"] },
    { title: "Statutory Config", path: "/eduapp/hr/statutory",         icon: <ScrollText className="h-4 w-4" />,    permissions: ["payroll:config", "payroll:read"] },
    { title: "Proration Policy", path: "/eduapp/hr/proration-policy",  icon: <CalendarCheck className="h-4 w-4" />, permissions: ["payroll:config", "payroll:read"] },
  ],
}
```
(Keep "Staff Management" where it is — HR is the pay/statutory layer on top of staff. All icons
used above are already imported in `adminNavigation.tsx`.)

---

## 8. Rules & guardrails

- **Never** compute PAYE/NSSF/SHIF/AHL/proration on the client — display backend values only.
- Disable transition actions the current status does not allow; never rely on the button alone
  (the backend enforces it and returns 409).
- Disable **Approve** on a run the current user processed (SoD) and when it has blocking errors.
- Prevent duplicate run creation for an existing month/year; surface the backend 409 message.
- `GET` endpoints that can legitimately be empty (e.g. compensation not yet set,
  `getStatutoryConfig`, `getEffectiveProration`) should pass `{ showErrorToast: false }` and render
  an empty/"not configured" state instead of a toast.
- Permission-gate every page and nav item; hiding UI is not a substitute for the backend's
  `@PreAuthorize`.
- Verify with `npx tsc --noEmit` and `npm run build` before considering the work done.

---

## 9. Definition of done (frontend)

- `src/types/hr.ts` and `src/lib/api/hrApi.ts` match the endpoints/DTOs above; no dead calls, no `any`.
- Compensation, statutory config, proration policy, payroll run list/detail, and payslip pages work
  end-to-end against the running backend.
- A run can be created → processed → approved by a **different** user → marked paid; payslips render
  the correct grouped statutory breakdown; the statutory summary shows a reconciled badge.
- SoD is reflected (a processor cannot approve their own run in the UI, and the backend 409 is
  surfaced if attempted).
- Navigation is permission-aware; no dead routes.
- `tsc --noEmit` and the production build pass.

---

## 10. Out of scope (Phase 1.5+)

- Staff self-service "My Payslips" (uses `payroll:self:read`; identity-scoped to the caller's own
  payslips — the backend ownership link already exists).
- Payslip PDF and payroll register / statutory schedule exports.
- Any Finance/Expenses ledger posting UI (Phase 2).
