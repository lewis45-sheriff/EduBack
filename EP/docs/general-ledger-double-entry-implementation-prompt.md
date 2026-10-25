# Double-Entry General Ledger (Chart of Accounts + Journals) — Full Implementation Prompt

> Supersedes `payroll-ledger-posting-implementation-prompt.md`. That earlier prompt described a
> lightweight single-line posting into the existing single-entry ledger. This prompt replaces that
> approach with a proper **double-entry General Ledger** that becomes the accounting backbone for
> **every money event**: student fee payments (manual + M-Pesa + bank callbacks), transport fees
> (kept separate), supplier payments/expenses, and payroll — with **reversals** throughout, and a
> **frontend** to view accounts, journals and the trial balance.

## 1. Role and objective

Act as a senior full-stack engineer (Spring Boot / Java 21 backend at
`C:\Users\USER\Desktop\EP\EduBack\EP`; Next.js/TypeScript frontend at
`C:\Users\USER\Desktop\SchoolManagement\schoolapp`). The system is multi-tenant (shared schema;
`tenant_id` stamped by `TenantEntityListener` on every `TenantScopedEntity`; reads isolated by the
Hibernate `tenantFilter`).

Build a **double-entry General Ledger (GL)**:
- A **Chart of Accounts** (GL accounts with type, code, name, normal balance).
- **Journal entries** made of balanced debit/credit **lines** (sum of debits == sum of credits).
- **Automatic posting** from every money event to the correct accounts.
- **Reversal** of any journal via a maker-checker request that posts a *counter-journal* (GL is
  append-only; never delete/mutate posted lines).
- **Account balances / trial balance** derived from journal lines.
- A **frontend** under Finance to browse the chart of accounts, journal entries, an account ledger
  (statement), and a trial balance.

This is a new accounting subsystem. Correctness, balance integrity (debits == credits always),
idempotency, tenant isolation and auditability are the priorities. Do not break any existing money
flow while adding the GL posting.

## 2. Mandatory discovery before coding (verified integration map)

Read and confirm these before editing. The GL must hook into the finalizing method of each money
event — do not post from controllers or from the "pending/maker" step.

| Money event | Finalizing service + method | Entity / key fields | Where to hook |
|---|---|---|---|
| **Student fee payment (manual, M-Pesa, bank all route here)** | `FinanceTransaction/FinanceTransactionServiceImpl.createTransaction(Long studentId, CreateTransactionDTO dto)` | `FinanceTransaction` — id, studentId, studentName, transactionType (INCOME\|EXPENSE), category, amount, transactionDate, paymentMethod, reference, invoiceId, source (MANUAL/MPESA_CALLBACK/MPESA_STK/PAYMENT_TRANSFER/SYSTEM) | Right after `financeTransactionRepository.save(transaction)` (next to the existing `ledgerService.recordCredit` call). Handle **both** INCOME and EXPENSE. |
| **M-Pesa STK callback** | `MPesa/MpesaServiceImpl.processCallback(...)` → builds DTO → calls `createTransaction` | reaches GL via the fee hook above | **No separate hook** — covered by the fee hook. Just verify. |
| **M-Pesa C2B / paybill** | `MpesaPaybill/MpesaPaybillServiceImpl.processCallBack(...)` → `createTransaction` | same | covered by the fee hook. |
| **Bank callback** | `BankIntergration/BankServiceImpl.postTransactions(BankRequestDTO)` → `createTransaction` | same | covered by the fee hook. |
| **Transport fee payment (SEPARATE accounts)** | `Transport/TransportTransactions/Approval/PendingTransportTransactionServiceImpl.approve(Long id)` | `TransportTransactions` — id, amount (**Double**, convert to BigDecimal), student, transport (route/vehicle), term, year, transactionTime, status; **no reference field** (synthesize `"TRTXN-"+id`) | After `transportTransactionsRepository.save(transaction)`. Use transport-specific GL accounts. |
| **Supplier payment** | `Procurement/SupplierPayments/SupplierPaymentServiceImpl` approve path (currently `ledgerService.recordDebit`) | payment amount, supplier, referenceNumber, paymentDate | at the approve/post point. |
| **Expense** | `Expenses/ExpensesServiceImpl.approveExpense(Long id)` (currently `ledgerService.recordDebit`) | amount, expenseNumber, expenseDate, category, description | at approve. |
| **Payroll** | `HR/payroll/service/PayrollServiceImpl.markPaid(Long runId, MarkPaidRequest)` | `PayrollRun` totals: totalGross, totalPaye, totalNssfEmployee, totalNssfEmployer, totalShif, totalAhlEmployee, totalAhlEmployer, totalNita, totalOtherDeductions, totalNet, totalEmployeeDeductions, totalEmployerContributions, totalEmployerCost; id + period | after `runRepository.save(run)`. |

Also read:
- `Procurement/Ledger/*` (existing single-entry `LedgerEntry`, `LedgerServiceImpl` with hardcoded
  `FEE_PAYMENT`/`SUPPLIER_PAYMENT` referenceTypes, `LedgerController` read-only at `/api/v1/ledger`).
  **Leave it intact** — the new GL runs in parallel (see §7).
- `TransactionReversal/TransactionReversalServiceImpl` — the maker-checker reversal convention
  (request entity + snapshot + status PENDING_APPROVAL→APPROVED/REJECTED, maker != checker). The GL
  reversal must follow this convention but post a **counter-journal** instead of deleting rows.
- `Authentication/Enum/Permissions.java` — reuse `ledger:read`; add new GL permissions (§6).
- `Multitenancy/base/TenantScopedEntity.java`, `Multitenancy/config/HibernateFilterInterceptor.java`
  — GL entities extend `TenantScopedEntity`; posting runs under an authenticated tenant context.

Do not assume a chart of accounts exists — there is none today. Do not assume the existing ledger is
double-entry — it is single-entry and stays as-is.

## 3. Data model (new) — `com.EduePoa.EP.GeneralLedger`

All entities extend `TenantScopedEntity`. Money = `BigDecimal(19,2)`.

### 3.1 `GlAccount` (chart of accounts)
- `id`, `code` (String, e.g. `"1000"`, unique per tenant), `name` (e.g. "Cash/Bank at hand"),
  `GlAccountType type` enum { `ASSET`, `LIABILITY`, `EQUITY`, `INCOME`, `EXPENSE` },
  `NormalBalance normalBalance` enum { `DEBIT`, `CREDIT` } (ASSET/EXPENSE=DEBIT; LIABILITY/EQUITY/INCOME=CREDIT),
  `String systemKey` (nullable, unique per tenant — a stable machine key the posting code resolves
  accounts by, e.g. `CASH_BANK`, `FEES_INCOME`, `TRANSPORT_INCOME`, `STUDENT_AR`, `SALARIES_EXPENSE`,
  `PAYE_PAYABLE`, `NSSF_PAYABLE`, `SHIF_PAYABLE`, `AHL_PAYABLE`, `NITA_PAYABLE`, `NET_PAY_PAYABLE`,
  `EMPLOYER_STATUTORY_EXPENSE`, `SUPPLIER_AP`, `EXPENSE_GENERAL`),
  `boolean active`, `boolean system` (system accounts can't be deleted), `description`.
- Unique constraints per tenant on `(tenant_id, code)` and `(tenant_id, systemKey)` where systemKey not null.

### 3.2 `JournalEntry` (header)
- `id`, `entryNumber` (per-tenant sequence, e.g. `"JE-000123"`), `LocalDate entryDate`,
  `String sourceType` (e.g. `FEE_PAYMENT`, `TRANSPORT_FEE`, `PAYROLL`, `SUPPLIER_PAYMENT`, `EXPENSE`,
  `MANUAL`, `REVERSAL`), `Long sourceId` (the originating row id; for reversal = reversed journal id),
  `String referenceNumber`, `String description`, `JournalStatus status` enum { `POSTED`, `REVERSED` },
  `Long reversalOfJournalId` (nullable — set on counter-journals), `boolean reversed` (true once a
  counter-journal exists), `User createdBy`, `LocalDateTime createdAt`.
- **Idempotency:** unique per tenant on `(tenant_id, sourceType, sourceId)` for non-reversal entries,
  so re-posting the same money event is a no-op/ skip. Reversal entries are keyed by
  `(REVERSAL, reversedJournalId)`.

### 3.3 `JournalLine`
- `id`, `JournalEntry journalEntry` (ManyToOne), `GlAccount account` (ManyToOne),
  `BigDecimal debit` (>=0), `BigDecimal credit` (>=0) — exactly one of the two > 0 per line,
  `String narration`, `int lineOrder`.
- **Balance invariant enforced in code:** sum(debit) == sum(credit) and total > 0 before persist;
  reject otherwise with a clear error. GL lines are **append-only** — never update/delete after post.

### 3.4 Account balance
Do not store a running balance on the account (the single-entry ledger's global running balance is
exactly the limitation we're fixing). Derive balances from lines via repository aggregation:
`balance(account) = Σdebit − Σcredit` (present signed per the account's normal balance in the DTO).

## 4. Posting service — `GeneralLedgerService`

Core API (all tenant-scoped, idempotent, append-only):
```java
JournalEntry post(PostJournalCommand cmd);   // builds + validates balanced lines, dedup on (sourceType, sourceId)
JournalEntry reverse(Long journalId, String reason, User actor); // posts a counter-journal, flips reversed flag
```
- `PostJournalCommand` = { sourceType, sourceId, referenceNumber, entryDate, description, List<Line> }
  where `Line` = { accountSystemKey (or accountId), debit, credit, narration }.
- `post(...)`:
  1. If an entry already exists for `(sourceType, sourceId)` → log + return it (no duplicate).
  2. Resolve each `accountSystemKey` to the tenant's `GlAccount` (auto-create missing **system**
     accounts from a seed definition if absent — see §5).
  3. Validate Σdebit == Σcredit and > 0; else throw `GlImbalanceException`.
  4. Persist header + lines; assign `entryNumber`.
- `reverse(...)`: load the journal; if already `REVERSED` → conflict. Create a new `JournalEntry`
  (`sourceType=REVERSAL`, `sourceId=reversedJournalId`, `reversalOfJournalId=reversedJournalId`) whose
  lines are the **debit/credit swap** of the original; mark the original `reversed=true` /
  `status=REVERSED`. Never delete the original.
- Posting from money events must be **best-effort and non-blocking**: wrap the `post(...)` call in
  try/catch at each hook and run the GL write in `@Transactional(propagation = REQUIRES_NEW)` so a GL
  failure logs a warning but never rolls back the underlying payment/payroll operation (mirror how
  `FinanceTransactionServiceImpl` already guards its `ledgerService.recordCredit`).

## 5. Seeded chart of accounts (per tenant)

Add a `GlAccountSeeder` (an idempotent per-tenant seeder, mirroring `StatutoryConfigSeeder`/
`CurriculumSeeder`) and wire it into `TenantProvisioningService.provisionTenant(...)` (after the
statutory seeding) so every **new** tenant gets the default chart, and its `run(ApplicationArguments)`
seeds existing tenants at boot. Seed these system accounts (code/name/type/normalBalance/systemKey):

| Code | Name | Type | Normal | systemKey |
|---|---|---|---|---|
| 1000 | Cash/Bank | ASSET | DEBIT | `CASH_BANK` |
| 1100 | Student Fees Receivable | ASSET | DEBIT | `STUDENT_AR` |
| 4000 | School Fees Income | INCOME | CREDIT | `FEES_INCOME` |
| 4100 | Transport Fees Income | INCOME | CREDIT | `TRANSPORT_INCOME` |
| 2000 | Accounts Payable (Suppliers) | LIABILITY | CREDIT | `SUPPLIER_AP` |
| 2100 | Net Pay Payable | LIABILITY | CREDIT | `NET_PAY_PAYABLE` |
| 2200 | PAYE Payable | LIABILITY | CREDIT | `PAYE_PAYABLE` |
| 2300 | NSSF Payable | LIABILITY | CREDIT | `NSSF_PAYABLE` |
| 2400 | SHIF Payable | LIABILITY | CREDIT | `SHIF_PAYABLE` |
| 2500 | Housing Levy (AHL) Payable | LIABILITY | CREDIT | `AHL_PAYABLE` |
| 2600 | NITA Payable | LIABILITY | CREDIT | `NITA_PAYABLE` |
| 5000 | Salaries & Wages Expense | EXPENSE | DEBIT | `SALARIES_EXPENSE` |
| 5100 | Employer Statutory Contributions | EXPENSE | DEBIT | `EMPLOYER_STATUTORY_EXPENSE` |
| 6000 | General Expenses | EXPENSE | DEBIT | `EXPENSE_GENERAL` |

Figures/accounts are a sensible Kenyan default; admins can rename/add accounts, but system accounts
(by `systemKey`) cannot be deleted because posting resolves them by key.

## 6. Posting rules per money event (the journals)

> School fees and transport fees post to **different income accounts** (`FEES_INCOME` vs
> `TRANSPORT_INCOME`) so they are tracked separately, as required.

- **Student fee payment (INCOME)** — source `FEE_PAYMENT`, sourceId = `FinanceTransaction.id`:
  - DR `CASH_BANK` (amount), CR `FEES_INCOME` (amount).
  - (Keep it simple/cash-basis; do not attempt AR relief unless you also post the invoice accrual.
    If you later post invoice accruals, switch to DR CASH_BANK / CR STUDENT_AR. For now, cash-basis
    DR CASH_BANK / CR FEES_INCOME.)
- **Student fee EXPENSE / refund (EXPENSE)** — DR `EXPENSE_GENERAL` (or a refund contra), CR `CASH_BANK`.
- **Transport fee payment** — source `TRANSPORT_FEE`, sourceId = `TransportTransactions.id`:
  - DR `CASH_BANK`, CR `TRANSPORT_INCOME`.
- **Supplier payment** — source `SUPPLIER_PAYMENT`, sourceId = payment id: DR `SUPPLIER_AP`
  (or `EXPENSE_GENERAL` if no AP accrual), CR `CASH_BANK`.
- **Expense approval** — source `EXPENSE`, sourceId = expense id: DR `EXPENSE_GENERAL`, CR `CASH_BANK`.
- **Payroll mark-paid** — source `PAYROLL`, sourceId = `PayrollRun.id`. One balanced journal:
  - DR `SALARIES_EXPENSE` = `totalGross`
  - DR `EMPLOYER_STATUTORY_EXPENSE` = `totalEmployerContributions` (employer NSSF + employer AHL + NITA)
  - CR `PAYE_PAYABLE` = `totalPaye`
  - CR `NSSF_PAYABLE` = `totalNssfEmployee + totalNssfEmployer`
  - CR `SHIF_PAYABLE` = `totalShif`
  - CR `AHL_PAYABLE` = `totalAhlEmployee + totalAhlEmployer`
  - CR `NITA_PAYABLE` = `totalNita`
  - CR `NET_PAY_PAYABLE` = `totalNet`
  - (Σ debits = totalGross + totalEmployerContributions = `totalEmployerCost`; Σ credits must equal
    the same — assert balance before posting. If rounding leaves a 1-cent gap, post the difference to
    the smallest statutory line or `EXPENSE_GENERAL` and log it.)
  - **Marking paid does not move cash**; cash leaves when you later settle net pay / remit statutory.
    If you want "paid" to also clear cash immediately, add a second journal DR each payable + CR
    `CASH_BANK`; otherwise leave the payables open for a future "remittance" step (recommended; note
    it as a follow-up).

## 7. Coexistence with the existing single-entry ledger

- Keep `Procurement.Ledger` (`/api/v1/ledger`) working and its callers unchanged — it remains a
  simple cash-movement view during transition.
- The new GL posts **in parallel** from the same hooks. Do **not** rip out the existing
  `ledgerService.recordCredit/recordDebit` calls in this phase; add the GL `post(...)` alongside.
- The new Finance → "General Ledger" frontend (below) is the forward-looking view; the old
  `/eduapp/procurement/ledger` page stays until you decide to retire it.

## 8. Reversal (maker-checker, aligned with `TransactionReversal`)

- New entity `GlReversalRequest` (snapshot of target journal: journalId, entryNumber, sourceType,
  amount, reason) with `status` { PENDING_APPROVAL, APPROVED, REJECTED }, maker-checker fields.
- Flow mirrors `TransactionReversalServiceImpl`:
  - `createGlReversal(journalId, reason)` — maker (`journal_reversal:create`). Validate the journal is
    `POSTED` and not already reversed / pending.
  - `approveGlReversal(id)` — checker (`journal_reversal:approve`), maker != checker enforced; calls
    `generalLedgerService.reverse(journalId, reason, actor)` to post the counter-journal; sets APPROVED.
  - `rejectGlReversal(id, reason)`.
- **Payroll reversal:** add a `PAID → REVERSED` transition. New `PayrollStatus.REVERSED` (or a
  `reversed` flag), a `payroll:reverse` (or reuse `payroll:process`) guarded endpoint
  `POST /api/v1/hr/payroll/runs/{id}/reverse` that (a) reverses the payroll GL journal via
  `generalLedgerService.reverse(...)` and (b) moves the run to REVERSED. Keep it maker-checker if you
  want; minimally require `payroll:approve`. Document the chosen rule.
- Reversing is **append-only**: always a counter-journal, never a delete. The original journal and its
  counter both remain visible, linked by `reversalOfJournalId`.

## 9. Backend endpoints — `/api/v1/gl`

- `GET /gl/accounts` (`gl:read`) — chart of accounts (+ computed balances, optional `asOf`).
- `POST /gl/accounts` / `PUT /gl/accounts/{id}` (`gl:manage`) — create/edit non-system accounts.
- `GET /gl/journals?sourceType&from&to&page&size` (`gl:read`) — paginated journal headers.
- `GET /gl/journals/{id}` (`gl:read`) — header + lines.
- `GET /gl/accounts/{id}/ledger?from&to&page&size` (`gl:read`) — account statement (running balance
  computed in the query/DTO, per account).
- `GET /gl/trial-balance?asOf` (`gl:read`) — per-account total debit/credit and the grand totals
  (must balance).
- `POST /gl/journals` (`gl:post`) — optional manual balanced journal (validate debits==credits).
- `POST /gl/reversals/{journalId}` (`journal_reversal:create`), `PUT /gl/reversals/{id}/approve`
  (`journal_reversal:approve`), `PUT /gl/reversals/{id}/reject`, `GET /gl/reversals` — reversal flow.

All return the standard `CustomResponse<T>` envelope `{ message, statusCode, entity }`. Add to
`SecurityConfig` only if any are public (none should be — all require auth).

### Permissions to add (Permissions enum, pattern `NAME("x:y","desc")`)
`gl:read`, `gl:post`, `gl:manage`, `journal_reversal:create`, `journal_reversal:approve`, and
(optional) `payroll:reverse`. Grant them to `School_Admin` in `TenantProvisioningService`
(all-permissions-except-MANAGE_TENANTS already covers new enum values automatically — verify).

## 10. Frontend — `C:\Users\USER\Desktop\SchoolManagement\schoolapp`

Follow existing conventions: API client `src/lib/api/glApi.ts` using `apiClient` + `ApiEnvelope<T>`;
types `src/types/gl.ts`; pages under `src/app/eduapp/(admindashboard)/finance-gl/` (or nest under the
existing Financial Management group); permission gating via `usePermissions()`; shared UI primitives;
`PaginationBar`; `formatKES` convention; tabs via the existing `Tabs` primitive.

Add a **General Ledger** area (navigation entry under Financial Management, gated on `gl:read`) with:
1. **Chart of Accounts** — table of accounts (code, name, type, balance); create/edit non-system
   accounts when `gl:manage`.
2. **Journal Entries** — paginated list (entry no., date, sourceType badge, description, debit total,
   status POSTED/REVERSED); row → detail showing balanced debit/credit lines; a **Reverse** action
   (maker) when `journal_reversal:create` and not already reversed.
3. **Account Ledger / Statement** — pick an account + date range → running statement.
4. **Trial Balance** — per-account debit/credit columns with grand totals that visibly balance; date
   filter; CSV/print optional.
5. **Reversal approvals** — a queue for `journal_reversal:approve` (approve/reject with reason),
   mirroring the existing Approvals UX.

Update the payroll **mark-paid** dialog copy (`hr/payroll/RunActionDialogs.tsx`) to state that
marking paid posts a payroll journal to the General Ledger (salaries expense + statutory payables),
and that it does not move cash/initiate a bank transfer. Add a **Reverse run** affordance on a PAID
run’s detail page if you implement the payroll reversal endpoint.

All pages: loading / empty / error / access-denied states; no `any`; reuse existing components; money
right-aligned; never compute balances the backend didn’t return (render backend figures).

## 11. Guardrails
- **Balance invariant is sacred:** every journal must have Σdebit == Σcredit > 0; reject otherwise.
- GL is **append-only**: never update or delete posted journals/lines; corrections are counter-journals.
- **Idempotent posting:** dedup on `(sourceType, sourceId)`; re-posting a money event is a no-op.
- **Non-blocking:** GL posting failures must never roll back or fail the underlying fee/transport/
  payroll operation (try/catch + `REQUIRES_NEW`), exactly like the current ledger hook.
- **Tenant safety:** GL entities extend `TenantScopedEntity`; never set `tenant_id` manually; posting
  runs under the authenticated tenant context (the money-event methods already do).
- Keep school fees and transport fees on **separate income accounts**.
- Do not remove or alter the existing single-entry ledger or its callers in this phase.
- Do not change payroll calculation or the existing approve/reject/mark-paid guards beyond adding the
  reverse transition.

## 12. Testing & verification
Backend: `./mvnw -q compile`, then `./mvnw clean package -DskipTests`; run existing tests; add unit
tests for (a) the balance validator (rejects imbalanced journals), (b) idempotent re-post, (c) the
payroll journal amounts == the run totals and that it balances, (d) reverse() produces a swapped,
balanced counter-journal and flips `reversed`. Then restart and verify against the running backend:
1. Record a student fee payment → one balanced `FEE_PAYMENT` journal (DR CASH_BANK / CR FEES_INCOME).
2. An M-Pesa STK callback and a bank callback for a student → each yields exactly one `FEE_PAYMENT`
   journal through `createTransaction` (no duplicates; dedup on FinanceTransaction id).
3. Approve a transport fee → one `TRANSPORT_FEE` journal to `TRANSPORT_INCOME` (separate from fees).
4. Mark a payroll run paid → one balanced `PAYROLL` journal with the per-statutory payable credits
   and the salaries/employer expense debits; Σdebit == Σcredit == totalEmployerCost.
5. Reverse a journal (maker → checker) → a counter-journal appears, original marked REVERSED, trial
   balance still balances.
6. Trial balance endpoint: total debits == total credits across all accounts.
7. Regression: existing `/api/v1/ledger` still works; fees/transport/payroll operations still succeed
   even if GL posting is forced to fail (best-effort).
Frontend: `npx tsc --noEmit`, `npm run build`, lint; manually verify the five GL views and gating.
Clearly separate automated results from manual/reasoned checks.

## 13. Definition of done
- Chart of accounts seeded per tenant (new + existing) and at provisioning; system accounts protected.
- Every money event (fee manual/M-Pesa/bank, transport-separate, supplier, expense, payroll) posts a
  balanced, idempotent journal to the correct accounts; posting is non-blocking.
- Reversal (maker-checker) posts counter-journals; payroll gains a PAID→REVERSED reverse action.
- Read endpoints: accounts (+balances), journals (+lines), account statement, trial balance (balances).
- Frontend GL area: chart of accounts, journals, account statement, trial balance, reversal queue —
  permission-gated with full states.
- Existing single-entry ledger and all current money flows remain intact.
- Backend build/package + tests pass; frontend tsc/build/lint pass; the workflow checks above were
  actually executed (or gaps documented).

## 14. Out of scope (note as future work)
- Accrual accounting / invoice accruals to Student AR (current fee posting is cash-basis).
- A statutory **remittance** step that clears PAYE/NSSF/SHIF/AHL/NITA payables when you pay the
  authorities (and the net-pay disbursement journal that moves cash). Recommended next phase.
- Multi-currency, cost centers, fiscal-period close/lock, retained-earnings roll-forward.
- Retiring the old `/eduapp/procurement/ledger` page.
