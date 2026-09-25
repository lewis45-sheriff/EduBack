# Frontend Prompt — Bulk Reverse (Void) Invoices: School-wide & Per-grade

Add the ability to reverse many invoices at once: every invoice for a term across the whole school, or every invoice for a single grade in a term. Reversing voids each invoice, resets that student's Finance rollup for the term/year, releases any optional-fee assignments the invoice consumed (so they can be re-invoiced), and frees the term so a new invoice can be generated for that student.

These are bulk operations. They process invoices one at a time; a single invoice that cannot be reversed (for example, one that already has a payment) is skipped and reported, and does **not** stop the rest of the batch.

Reuse the app's existing HTTP client, auth interceptor, error handling, and design system.

All requests use port **8085** (backend), bearer JWT, and the standard envelope:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Drive UI from `statusCode`; show `message` on errors.

## Permission
`invoice:reverse` — gate both bulk controls on this. The role must have it granted. Hide the controls entirely for users without it.

## Endpoints

### A. Reverse all invoices school-wide for a term
`POST /api/v1/invoicing/reverse-all/{term}?academicYear=2026`

- Path param `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`.
- Query param `academicYear` optional (defaults to the current year). No body.
- Reverses every non-reversed invoice for that term/year.

### B. Reverse all invoices for a grade for a term
`POST /api/v1/invoicing/reverse/grade/{gradeId}/{term}?academicYear=2026`

- Path params `gradeId` (Long) and `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`.
- Query param `academicYear` optional (defaults to the current year). No body.
- Reverses every non-reversed invoice for students in that grade for the term/year.

## Success response (both A and B)
`200`, `entity` is a summary of the batch:
```json
{
  "totalInvoices": 120,
  "reversedInvoices": 118,
  "failedInvoices": 2,
  "skippedInvoices": 2,
  "term": "TERM_1",
  "academicYear": 2026,
  "reversed": [
    { "studentId": 10, "studentName": "Ann Doe", "status": "Reversed", "invoiceId": 1000 }
  ],
  "failed": [
    { "studentId": 22, "studentName": "Jon Kay", "status": "Invoice 1044 has payments of 5000 recorded and cannot be reversed. Reverse or refund the payment first.", "invoiceId": 1044 }
  ]
}
```

Field meaning:
- `totalInvoices` — how many invoices were in scope for the term/year (and grade, for B).
- `reversedInvoices` — successfully voided.
- `failedInvoices` — could not be reversed; each has a reason in `failed[].status`.
- `skippedInvoices` — invoices blocked by a business rule (already reversed, or payments recorded). These are also listed in `failed[]` with the reason, so `failedInvoices` includes the skipped ones.
- `reversed[]` / `failed[]` — per-invoice detail: `studentId`, `studentName`, `status` (outcome or reason), `invoiceId`.

`message` restates the counts, e.g. `"School-wide reversal completed for TERM_1 2026: 118 reversed, 2 failed, 2 skipped out of 120 invoices"`.

## Error handling
- `400` — missing required path/params (`term`, or `gradeId` for B). Show `message`.
- `500` — unexpected; show `message`. Note: individual invoices that fail do **not** cause a 500 — they come back inside `failed[]` with a `200` overall.

Because the batch is best-effort, a `200` can still contain failures. Always inspect the summary, not just the status code.

## Reversal history
Every reversed invoice (single, student, grade, or school-wide) is saved and listed by `GET /api/v1/invoicing/reversals` (permission `invoice:reverse`), newest first. Each record carries the invoice snapshot, `reversedBy`, `reversedAt`, `releasedOptionalFees`, and a `scope` of `SINGLE` | `STUDENT` | `GRADE` | `SCHOOL_WIDE`. Bulk reversals from these endpoints produce records with scope `SCHOOL_WIDE` (A) or `GRADE` (B). See the single-invoice reversal prompt for the full response shape.

Invoice-listing endpoints exclude reversed/deleted invoices, so a reversed invoice disappears from the normal lists and appears only under `reversals`.

## What happens on the backend (so the UI can reflect it)
- Each reversed invoice is **removed** (voided) — it will no longer appear in invoice lists.
- Each affected student's `Finance` for that term/year is reset (its fee total for that invoice goes to zero; `paidAmount` is preserved).
- Optional-fee assignments that were invoiced are flipped back to `invoiced = false` and remain **active** — they'll be included again the next time you generate an invoice for that term.
- The term/year is freed per student, so `POST /api/v1/invoicing/create/{studentId}/{term}` (or a fresh `invoice-all`) can generate new invoices.
- An invoice with `amountPaid > 0` is **not** reversed; it is reported in `failed[]`/`skippedInvoices` and left unchanged.
- The operation is best-effort per invoice (not one big transaction): invoices already reversed before an error remain reversed.

## What to build
1. **API client**: add
   - `reverseAllInvoices(term, academicYear?)` → `POST /invoicing/reverse-all/{term}`
   - `reverseGradeInvoices(gradeId, term, academicYear?)` → `POST /invoicing/reverse/grade/{gradeId}/{term}`
2. **Bulk reverse actions** (visible only with `invoice:reverse`), for example on the invoicing dashboard and on a grade view:
   - "Reverse all for term" — pick a term (and optionally academic year).
   - "Reverse grade for term" — pick a grade + term (and optionally academic year).
   - Each behind a strong confirmation dialog that names the term (and grade), warns it will void many invoices and release their optional fees, and notes that invoices with payments are skipped. Consider a type-to-confirm step given the blast radius.
3. **Result view**: after a `200`, render the summary — reversed / failed / skipped counts — and a table of `failed[]` with each `studentName` and reason so the user can resolve payment-blocked invoices individually (via the single-invoice reverse flow after refunding).
4. **Refresh**: on success, refresh invoice lists, affected student balances/Finance views, and optional-fee lists (rows return to `invoiced = false`).
5. **Loading / disable state**: bulk runs can take a while; show progress and disable the trigger until the response returns to avoid double-submits.

## Acceptance checks
- Reversing all invoices for a term with no payments returns `200`, `reversedInvoices == totalInvoices`, empties those invoices from the list, and resets balances for the term.
- Reversing a grade only affects that grade's invoices for the term; other grades are untouched.
- When some invoices have payments, the call still returns `200`; those invoices appear in `failed[]` with a payment-blocked reason, are counted in `skippedInvoices`, and remain unchanged.
- After a bulk reversal, generating invoices again for the same term (per student or `invoice-all`) succeeds and re-includes the released optional fees.
- Omitting `academicYear` targets the current year; passing it targets that year.
- Both bulk controls are hidden for users without `invoice:reverse`.
