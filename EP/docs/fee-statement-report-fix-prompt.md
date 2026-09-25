# Prompt — Fix the Student Fee Statement Report + Add a Payments/Transactions Table

Refine this prompt, then hand it back for implementation.

## Problem (observed)
The generated **STUDENT FEE STATEMENT** PDF shows:
- Student Name and Admission No blank
- Current Term Fee, Balance Forward, Term Balance, Total Paid This Term all `null`
- Payments Recorded `0`, Overall Account Status `No Data`
- Remarks: "No fee record is available for the selected term."

## Root cause (from code investigation)
The report is a **JasperReports** template, `JasperReports/jrxmls/fee_statement.jrxml`, filled with **embedded SQL** over a **raw JDBC connection** (not Hibernate). Every metric comes from a single `finance` row selected by:
```sql
WHERE f.student_id = :studentID AND f.term = :termID AND f.year = :year
```
Because the template uses `whenNoDataType="AllSectionsNoDetail"`, when that lookup returns **no row** Jasper still renders the page with all fields null — exactly the broken output above.

Two reasons the lookup returns nothing:
1. **Term string mismatch.** `finance.term` is stored as an enum string (`TERM_1` / `TERM_2` / `TERM_3`). The fee-statement path passes the request's `term` through **unnormalized** (unlike the report-card path, which normalizes). If the caller sends anything but `TERM_1/2/3`, nothing matches.
2. **No tenant scoping.** The raw JDBC SQL has **no `tenant_id` predicate**, while `finance` / `finance_transactions` are tenant-scoped. Over a raw connection this can hit the wrong data or none.

## Goals
1. Make the statement show real values for a student who has a `finance` row for the term/year: name, admission no, current term fee, balance forward, term balance, total paid this term, payments recorded, overall status, and remarks.
2. Add / confirm a **Payments (transactions) table** listing each payment for that term: date, receipt/reference, payment method, amount, and a running balance.
3. When there genuinely is no data, keep a clean "No fee record…" message (don't show a table of nulls).

## Proposed changes (to refine)

### A. Fix the null values
- **Normalize the term** for the fee statement in `ReportsService.dynamicReportCreate` before putting `termID` into the Jasper parameters — map `1/2/3`, `Term 1`, `term_1`, etc. to `TERM_1/2/3`, same as the report-card path already does.
- **Add tenant scoping** to the `.jrxml` SQL: pass `tenantId` as a Jasper parameter (as the CBC report card already does) and add `AND f.tenant_id = :tenantId` and `AND ft.tenant_id = :tenantId`.
- Verify `year` (stored as integer) matches the `Long` passed in.

### B. Payments / transactions table
A payments detail band already exists in the template (RECEIPT NO. / DATE / PAYMENT MODE / AMOUNT / RUNNING TOTAL), sourced from `finance_transactions` where `transaction_type = 'INCOME'`, joined on `student_id + term + year`, ordered by date. It renders nothing today only because the parent `finance` row is missing. Once A is fixed it will populate. Enhancements to confirm:
- Columns: Date | Receipt/Reference | Payment Method | Amount (| Running Balance).
- Show a **totals row** (sum of payments = Total Paid This Term).
- Decide whether to include transfer postings (references `TRF-IN-*` / `TRF-OUT-*`) and whether EXPENSE/refund rows appear (currently INCOME only).
- Empty state: when there are no payments but a finance row exists, show "No payments recorded this term" instead of an empty table.

## Open decisions (please refine)
1. **Term input contract** — should the frontend always send `TERM_1/2/3`, or must the backend accept loose forms (`1`, `Term 1`)? (I'll normalize regardless, but confirm the canonical value.)
2. **Payments scope** — INCOME only, or also show EXPENSE/refunds and payment-transfer in/out rows in the table (perhaps as signed amounts)?
3. **Running balance basis** — running total of payments only, or a true account running balance (opening balance ± each posting)?
4. **Balance Forward definition** — sum of prior terms' balances for the same student (current behavior). Keep, or scope to same academic year only?
5. **Multi-term vs single-term** — statement is for one selected term/year. Do you also want an "all terms this year" variant?
6. **No-data behavior** — keep the single "No fee record is available" page, or return a 404 when the student truly has no finance record for the term?

## Acceptance criteria
- For a student with a finance row for the given term/year: name, admission no, current term fee, balance forward, term balance, total paid this term, and overall status all render real values (no `null`).
- The payments table lists each payment (date, reference, method, amount) with a running balance and a totals row equal to Total Paid This Term.
- The statement is tenant-safe: it only ever shows the requesting tenant's data.
- A student with no finance row shows the clean no-data message (or 404, per decision 6) — never a page of nulls.
- Loose term inputs (per decision 1) resolve correctly to the stored enum value.
