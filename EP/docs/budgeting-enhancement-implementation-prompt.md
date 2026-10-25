# Budgeting Module — Enhancement & Completion Implementation Prompt

## Context (read this first — do not skip)

A budgeting module **already exists** in this codebase. Your task is to **enhance and complete it**, not to build a new one or duplicate existing functionality. Before writing any code, inspect the current implementation and the finance modules it must integrate with, then produce a short assessment + plan, then implement incrementally.

This is a **multi-tenant Spring Boot (Java 21, MariaDB) backend** with a **Next.js (TypeScript) frontend**. Schema is managed by Hibernate `ddl-auto=update` + JPA annotations — **do NOT add Flyway or hand-written SQL migrations**; evolve the schema through entity annotations. Money is always `BigDecimal` (precision 19, scale 2). Every tenant-scoped entity extends `TenantScopedEntity` and is isolated by the Hibernate `tenantFilter` — never add manual `tenant_id` predicates; rely on the request-scoped filter exactly as existing GL/budget queries do.

### What already exists (confirmed) — reuse it

**Budget module** (`com.EduePoa.EP.Budget`):
- Entities: `Budget` (name, academicTerm, academicYear, authoritative `[startDate, endDate]` window, `status`, `totalIncomeAllocated`, `totalExpenseAllocated`, created/approved/closed audit fields, `@Version`), `BudgetCategory` (name, `CategoryType` INCOME/EXPENSE, `CategoryStatus`, and an **unused nullable `mappingKey`**), `BudgetLine` (one per category per budget, `allocatedAmount`), `BudgetLineMonthlyAllocation` (optional monthly phasing, sum ≤ line allocation).
- Enums: `BudgetStatus` (DRAFT/APPROVED/CLOSED), `CategoryType` (INCOME/EXPENSE), `CategoryStatus`, plus **`AmendmentStatus` and `PerformanceStatus` which are declared but UNUSED**.
- Services: `BudgetServiceImpl` and `BudgetCategoryServiceImpl` — **planning only** (create/update/approve/close, line management, monthly phasing, self-approval guard). No actuals, no variance, no dashboard, no reports, no amendments.
- Controllers: `BudgetController` (`/api/v1/budgets`), `BudgetCategoryController` — CRUD + lifecycle only.
- All services return `CustomResponse<?>`; replicate the existing private `status(...)` and `fail(...)` helpers and the `@Audit(module="BUDGET", action=...)` annotations.
- Frontend: existing `budget/` and `budget-categories/` pages under `src/app/eduapp/(admindashboard)/`. A `formatKES` helper lives in `budget/utils/budgetHelpers.ts`.

**General Ledger** (`com.EduePoa.EP.GeneralLedger`) — the authoritative source of actual money movement:
- Double-entry GL with posted `JournalEntry` / `JournalLine`, accounts keyed by stable `systemKey` (see `GlAccountKeys`: `FEES_INCOME`, `TRANSPORT_INCOME`, `SALARIES_EXPENSE`, `EMPLOYER_STATUTORY_EXPENSE`, `EXPENSE_GENERAL`, `CASH_BANK`, etc.).
- Balances are always **derived from posted journal lines** via SQL aggregation in `GlQueryService` / `JournalLineRepository` — never a stored running total.
- GL is already auto-posted from Finance transactions, M-Pesa, transport, supplier payments, expenses, and payroll (each via `GlPostingGateway`). This means **actuals already flow into the GL with no double-counting** — the GL is the single integration point for actual expenditure. Do NOT re-scan `Expenses`, payments, or payroll tables directly.

**Organizational hierarchy** — there is currently **NO** department / org-unit / cost-centre / division entity anywhere in the codebase (confirmed: no `Department`, `OrgUnit`, `CostCenter`, or HR designation entity exists). The hierarchy below must be introduced as new work; do not assume a reusable one exists, but DO re-check during inspection in case one has been added since.

**Tenant provisioning & default seeding pattern** — `TenantProvisioningService.provisionTenant(Tenant)` (package `com.EduePoa.EP.Multitenancy.service`) runs under a set `TenantContext` and, after creating default roles/admin/config, invokes a series of **idempotent, self-tenant-managing seeders** — `curriculumSeeder.seedForTenant(id)`, `statutoryConfigSeeder.seedForTenant(id)`, `glAccountSeeder.seedForTenant(id)` — each wrapped in its own try/catch that logs on failure without aborting provisioning, after which the tenant context is re-asserted. The default organizational hierarchy MUST be seeded the same way (see the hierarchy section).

**Permissions** (`Authentication/Enum/Permissions.java`) — already declared, wire them up:
- `BUDGET_CREATE` (`budget:create`), `BUDGET_READ` (`budget:read`), `BUDGET_MANAGE` (`budget:manage`), `BUDGET_APPROVE` (`budget:approve`), `BUDGET_CLOSE` (`budget:close`), `BUDGET_AMEND` (`budget:amend`, currently UNWIRED), `BUDGET_CATEGORY_MANAGE` (`budget_category:manage`), `BUDGET_REPORT_READ` (`budget_report:read`, currently UNWIRED).
- Controllers use `@PreAuthorize("hasPermission(null, '<perm:string>')")`. `role_permissions` stores the enum NAME; `CustomPermissionEvaluator` maps it back to `.getPermission()`.

## Core objective

Transform the planning-only budget module into a full **plan → allocate → track actuals → compare → alert → report** tool so school management can answer, at any time and per **organizational unit** / category / period: how much was budgeted, how much is actually spent, how much remains, whether spending is within / approaching / over budget, which units are overspending and by how much, and the school-wide picture against the total approved budget. Figures must **roll up through the organizational hierarchy** so a parent unit's total equals the sum of its own direct allocations plus all descendants.

## Organizational hierarchy (new, first-class budgeting dimension)

Introduce a configurable, tree-structured organizational hierarchy that budgets and actuals attach to and roll up through. This is the backbone of departmental budgeting and monitoring.

- **Entity `OrgUnit`** (tenant-scoped, extends `TenantScopedEntity`): `id`, `name`, `code` (unique per tenant, used for GL/reporting references), `type` (enum `OrgUnitType`, e.g. `SCHOOL`, `DIVISION`, `DEPARTMENT`, `SECTION`/`UNIT` — configurable set, not hardcoded business logic), self-referencing `parent` (`@ManyToOne`, nullable only for the single root), `status` (ACTIVE/INACTIVE), optional `headUser` (`@ManyToOne User`, the department head for scoping), audit fields, `@Version`.
- **Tree rules:** exactly one root per tenant (the school); every other unit has a parent; no cycles (validate on create/update). Keep the tree shallow-friendly but support arbitrary depth. Store and maintain a **materialized path** or depth/lineage helper (e.g. `path` string of ancestor codes) so descendant queries and roll-ups don't require recursive CTEs — or, if you prefer recursion, encapsulate it in one repository method. Document the chosen approach.
- **Hierarchy is fully configurable** by admins (add / rename / re-parent / deactivate units, define the `OrgUnitType` set). Provide CRUD with move/re-parent support that revalidates the tree and recomputes paths for the moved subtree. Nothing in the tree structure is hardcoded business logic.
- **Default structure seeded at tenant provisioning (required).** A newly provisioned tenant must NOT start with an empty hierarchy — seed a sensible default school tree so budgeting works on day one, exactly mirroring how the codebase already seeds the CBC curriculum, statutory payroll config, and the GL chart of accounts. Concretely:
  - Add an `OrgUnitSeeder` with an **idempotent** `seedForTenant(String tenantIdentifier)` that manages its own `TenantContext` (set in a try/finally, restored afterward) and uses explicitly tenant-scoped existence checks (`existsByTenantIdAndCode(...)`) — do NOT rely on the Hibernate request `tenantFilter` here, because provisioning runs outside a request (this is the exact bug class already fixed for the GL seeder).
  - Call it from `TenantProvisioningService.provisionTenant(...)` alongside `curriculumSeeder` / `statutoryConfigSeeder` / `glAccountSeeder`, inside its own try/catch that logs and does not abort provisioning, then re-assert the tenant context (same shape as the existing seeder calls).
  - **Default tree** (the root name/code may derive from the tenant's `schoolName`): root `SCHOOL` → common default departments such as `Academics`, `Administration`, `Finance`, `Transport`, `Catering/Meals`, `Boarding`, `Maintenance/Operations`, `ICT`. Keep this default list in one clearly-labelled constant so it is easy to adjust; it is a **starting point the admin can fully edit**, not a fixed taxonomy.
  - Because provisioning runs for existing tenants only once, also handle **back-fill for already-provisioned tenants**: make the seeder safe to run again (idempotent) and optionally expose an admin action / startup backfill runner (follow the existing `@Order`-ed `ApplicationRunner` backfill pattern used elsewhere) so current tenants get the default tree without re-provisioning. Re-running must never duplicate units.
- **Attachment points:** a `BudgetLine` gains an **optional `orgUnit`** (`@ManyToOne`, nullable = school-wide/unassigned). A budget can therefore allocate the same category to several units. Keep the existing one-line-per-category constraint **scoped per org unit** (one line per `(budget, category, orgUnit)`), not globally per category.
- **Roll-up aggregation:** any budget/actual figure for a unit = that unit's direct lines + the recursive sum of all descendant units' lines. Variance, utilization, and status all compute at every level. The root's totals equal the whole school.
- **Permissions:** add `ORG_UNIT_MANAGE` (`org_unit:manage`) for hierarchy CRUD. Reuse budget read/report permissions for viewing. Add a **scoping rule** so a user who is a unit's `headUser` (or assigned to it) sees only that unit and its descendants in departmental views — enforce in the service layer on top of the existing RBAC, do not build a parallel auth system.

## Authoritative calculation rules (define once, apply everywhere)

The dashboard, reports, and detail views MUST all use these same rules so numbers never disagree.

1. **Actuals come from the GL only.** For a budget line, resolve its category's `mappingKey` to a GL account `systemKey`, then compute net movement over the budget's `[startDate, endDate]` (inclusive, by `accountingDate`):
   - net movement `= sum(debit) − sum(credit)` for that account in the window.
   - **EXPENSE** category actual `= net debit` (the net movement). Positive = money spent.
   - **INCOME** category actual `= net credit` (negate the net movement). Positive = money earned.
   - Unmapped category (null/blank `mappingKey`) or `systemKey` not found for tenant → actual `= 0`, flagged `mapped = false` (never an error).
2. **Committed vs actual (avoid double counting).** Distinguish **committed** expenditure (e.g. approved purchase orders not yet paid) from **actual** expenditure (posted GL journals). Phase 1 tracks ACTUAL from the GL. If/when commitments are added, they are a **separate, additive figure** (`committed`) shown alongside actual — never summed into actual. Define `available = approved − actual − committed` and document it. Unpaid invoices and partial payments are reflected only to the extent they are posted to the GL; cancelled/reversed transactions are naturally netted out because GL reversals post counter-journals.
3. **Variance & status per allocation:**
   - EXPENSE line: `variance = approved − actual` (positive = under budget).
   - INCOME line: `variance = actual − approved` (positive = exceeded target).
   - `utilizationPercent = approved == 0 ? null : actual / approved × 100` (2 dp, HALF_UP).
   - Status from configurable thresholds: `WITHIN` (below warning), `APPROACHING` (≥ warning threshold, < 100%), `OVER` (> 100%). Map to the existing `PerformanceStatus` enum (extend it if its constants don't fit).
4. **Approved vs revised budget:** once amendments exist, the comparison baseline is the **current approved amount** (original + applied amendments); keep the original visible separately.
5. **Hierarchical roll-up:** a unit's figure = its direct lines + the recursive sum of all descendant units. Compute approved, actual, remaining, utilization, and status at every level; the root equals the whole school. Never double-count: each `BudgetLine` belongs to exactly one unit and is counted once at that unit and once in each ancestor's roll-up total (as a subtotal, not a duplicate direct line).
6. All money `BigDecimal` scale 2 HALF_UP; never divide by zero; coalesce null sums to zero.

## Required enhancements (map to the 10 requirements)

1. **Planning & approval** — exists; keep. Ensure approved budgets are immutable except through the amendment workflow (req 7).
2. **Allocation to org units & categories** — categories are already configurable (`BudgetCategory`). Add the **organizational hierarchy** (see section above): a new `OrgUnit` tree and an optional `orgUnit` on `BudgetLine`, with the one-line-per-category constraint re-scoped to `(budget, category, orgUnit)`. Keep categories configurable (salaries, teaching materials, utilities, maintenance, transport, meals, administration, other).
3. **Actual expenditure tracking** — integrate via the **GL only** (see rules). Wire `BudgetCategory.mappingKey` to GL `systemKey`; add admin UI to set it. No direct scanning of source tables; no duplicate entries.
4. **Budget-vs-actual** — add `BudgetActualsService` + variance assembly producing approved / actual / remaining / percent spent / variance per line and in total.
5. **Overspending alerts** — configurable warning threshold (per tenant default, optionally per budget/line); classify WITHIN / APPROACHING / OVER; surface in dashboard and (optionally) notifications via the existing notification mechanism if one exists.
6. **Departmental / hierarchical monitoring** — filter and roll up spending by org unit (any level of the tree), category, funding source, and period; return per-unit subtotals plus descendant roll-ups. Enforce role scoping so a unit's `headUser` sees only that unit and its descendants (reuse existing RBAC; add a service-layer scoping check rather than a new auth system).
7. **Revisions & supplementary budgets** — implement the amendment workflow using the existing `AmendmentStatus` enum and `budget:amend` permission: a `BudgetAmendment` entity (target line/budget, delta or new amount, reason, requestedBy/approvedBy, status, timestamps), approval step, full audit trail, original vs revised distinction. Applied amendments adjust the approved baseline atomically.
8. **Reporting** — budgeted vs actual, remaining, variance, overspending, and expenditure trends; filterable by financial year, academic year, term, org unit (with roll-up), category. Include a hierarchy (tree/drill-down) report showing each unit's approved/actual/remaining and its rolled-up totals. Guard with `budget_report:read`.
9. **Dashboard** — total approved budget, total actual expenditure, remaining, % utilized, count of over-budget allocations, count approaching limit, plus a per-top-level-unit breakdown with drill-down into the hierarchy.
10. **Auditability & access control** — reuse `@Audit`; record who created/approved/revised each change and when; apply the existing budget permissions (wire the unused `budget:amend` and `budget_report:read`); preserve historical records (never hard-delete approved/closed budgets or applied amendments).

## Suggested delivery phases (implement incrementally, verify each)

- **Phase 1 — Actuals & variance foundation (backend):** `JournalLineRepository.signedMovementBetween(accountId, from, to)`; `BudgetActualsService`; variance DTOs; `BudgetService.getVariance(budgetId)`; `GET /api/v1/budgets/{id}/variance` guarded by `budget_report:read`; category `mappingKey` admin endpoint/UI.
- **Phase 2 — Dashboard & reports:** aggregate endpoints (`/api/v1/budgets/dashboard`, report endpoints with filters) + frontend dashboard and report views reusing existing budget UI patterns and `formatKES`.
- **Phase 3 — Organizational hierarchy:** `OrgUnit` tree entity + `OrgUnitType` enum + CRUD/move with cycle validation and path maintenance (`org_unit:manage`); **`OrgUnitSeeder.seedForTenant(...)` wired into `TenantProvisioningService` for the default school tree, plus an idempotent back-fill for existing tenants**; optional `orgUnit` on `BudgetLine` with `(budget, category, orgUnit)` uniqueness; recursive roll-up in variance/dashboard/reports; `headUser`-based scoping; frontend hierarchy management + tree/drill-down views.
- **Phase 4 — Thresholds/alerts:** configurable thresholds (tenant default, optional per budget/line) + WITHIN/APPROACHING/OVER classification (per unit and rolled-up) + notifications.
- **Phase 5 — Amendments/supplementary budgets:** `BudgetAmendment` entity + workflow + approved-vs-revised reporting + audit.

## Conventions to follow (non-negotiable)

- Reuse the `Budget` package structure, `CustomResponse<?>` return shape, `status()/fail()` helpers, `@Audit`, and Lombok DTO style (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`).
- Tenant isolation via `tenantFilter` only; all new entities extend `TenantScopedEntity`.
- Schema via JPA annotations + `ddl-auto=update` — NO Flyway.
- Frontend: reuse existing budget components, API-client pattern, and the `PermissionString` union in `src/types/rbac.ts` (add new permission strings there if any are introduced).
- Do not touch unrelated modules; do not duplicate GL or finance logic.

## Explicit edge-case treatment (state in code + docs)

- Unpaid invoice: not an actual until posted to GL.
- Partial payment: actual = the posted portion only.
- Cancelled/reversed transaction: netted out automatically by GL counter-journals.
- Budget adjustment: changes the approved baseline only after the amendment is approved and applied.
- Multiple categories mapped to the same `systemKey`: disallow or clearly document (otherwise their actuals double-count across lines). Recommend enforcing uniqueness of `mappingKey` among active categories per tenant.
- **Org-unit actuals attribution:** the GL does not yet carry an org-unit dimension on journal lines, so Phase 3 roll-ups aggregate actuals by **category → systemKey** and distribute/roll up through the tree by how lines are allocated to units; actuals cannot be split below the account/category grain unless the GL is later extended with a unit tag. State this limitation explicitly and do not fabricate per-unit actuals that the GL can't support. If finer attribution is required, propose adding an optional org-unit reference on GL postings as separate, clearly-scoped work.
- **Re-parenting a unit:** must recompute the materialized path for the whole moved subtree atomically and must be blocked if it would create a cycle. Deactivating a unit with active budget lines or children must be guarded (block or cascade-with-confirmation, documented).
- **Deleting hierarchy with history:** never hard-delete a unit referenced by approved/closed budget lines; deactivate instead, preserving historical roll-ups.

## Deliverables & verification

1. **Assessment & plan first:** a concise write-up of what exists, what works, the gaps, and the phased plan — then implement.
2. **Per phase:** backend compiles (`.\mvnw.cmd -q -o compile`, exit 0), new unit tests pass (`BudgetActualsService` signing rules, variance math, amendment application, threshold classification), frontend `tsc --noEmit` + `eslint` + `build` pass.
3. **Functional checks:** map a category to a real `systemKey` (e.g. `TRANSPORT_INCOME`) for a tenant with posted journals and confirm variance reflects the posted movement; confirm 403 without `budget_report:read`, 200 with it; confirm cross-tenant isolation; confirm an approved budget cannot be edited except via an approved amendment; confirm dashboard/report/detail numbers agree; **provision a brand-new tenant and confirm the default school org tree is seeded automatically (root + default departments), and that re-running the seeder/back-fill does not duplicate units; build a multi-level org tree, allocate lines to leaf and mid units, and confirm each parent's rolled-up total equals the sum of its own direct lines plus all descendants (root = whole school); confirm re-parenting updates roll-ups and rejects cycles; confirm a `headUser` sees only their subtree.**
4. Do not claim tests pass unless they were actually run. Preserve uncommitted work; make changes additive.

## Expected outcome

School management gets a reliable, real-time budget-vs-actual view: allocate funds across a configurable organizational hierarchy (school → division → department → section) and by category, see actual spend sourced consistently from the GL, drill down and roll up spending at any level of the tree, catch overspending early via thresholds, control budget changes through an audited amendment workflow, and report by year/term/org unit/category — all within the existing multi-tenant architecture.
