# Frontend Prompt — Reverse (Void) Invoices

Add the ability to reverse a generated invoice. Reversing voids the invoice, resets the student's Finance rollup for that term/year, releases any optional-fee assignments it consumed (so they can be re-invoiced), and frees the term so a new invoice can be generated. Reuse the app's existing HTTP client, auth interceptor, error handling, and design system.

All requests use port **8085** (backend), bearer JWT, and the standard envelope:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Drive UI from `statusCode`; show `message` on errors.

## Permission
`invoice:reverse` — gate the reverse control on this. The role must have it granted.

## Endpoints

### A. Reverse a specific invoice by id
`POST /api/v1/invoicing/reverse/{invoiceId}`

- Path param `invoiceId`. No body.
- Success `200`, `entity` is the invoice snapshot as it was just before reversal:
  ```json
  {
    "invoiceId": 1000,
    "studentName": "Ann Doe",
    "admissionNumber": "ADM-10",
    "grade": "Grade 6",
    "feeMode": "DAY",
    "term": "TERM_1",
    "academicYear": 2026,
    "totalAmount": 31000.00,
    "amountPaid": 0,
    "balance": 0,
    "status": "P",
    "invoiceDate": "2026-09-24",
    "dueDate": "2026-10-24"
  }
  ```
  `message` states how many optional-fee assignments were released.

### B. Reverse a student's invoice for a term
`POST /api/v1/invoicing/reverse/student/{studentId}/{term}?academicYear=2026`

- `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`. `academicYear` optional (defaults to current year).
- Same success/error contract as A. Use this when you have the student + term but not the invoice id.

### List reversed invoices (reversal history)
`GET /api/v1/invoicing/reversals`

- Permission `invoice:reverse`. Returns every saved reversal operation, newest first.
- Each reversal is persisted at the moment of reversal (the invoice row itself is removed), so this is the durable record to show in a "Reversed invoices" screen.
- Success `200`, `entity` is an array:
  ```json
  [
    {
      "id": 7,
      "originalInvoiceId": 1000,
      "studentId": 10,
      "studentName": "Ann Doe",
      "admissionNumber": "ADM-10",
      "grade": "Grade 6",
      "term": "TERM_1",
      "academicYear": 2026,
      "totalAmount": 31000.00,
      "amountPaid": 0,
      "balance": 31000.00,
      "invoiceDate": "2026-09-24",
      "dueDate": "2026-10-24",
      "scope": "SINGLE",
      "releasedOptionalFees": 2,
      "reversedBy": "bursar@school.ac.ke",
      "reversedAt": "2026-09-25T10:15:00"
    }
  ]
  ```
- `scope` ∈ `SINGLE` | `STUDENT` | `GRADE` | `SCHOOL_WIDE` — how the reversal was triggered. `reversedBy` is the user who performed it; `releasedOptionalFees` is how many optional-fee assignments were freed.

### C. Reverse all invoices school-wide for a term
`POST /api/v1/invoicing/reverse-all/{term}?academicYear=2026`

- `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`. `academicYear` optional (defaults to current year). No body.
- This is a bulk operation: it reverses every non-reversed invoice for that term/year, one at a time. A single blocked invoice (e.g. one with payments) does **not** abort the rest — it is recorded and reporting continues.
- Success `200`, `entity` is a summary:
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
  `message` summarises the counts. Invoices blocked because they have payments are counted in both `skippedInvoices` and `failed` (with the reason in `status`).

### D. Reverse all invoices for a grade for a term
`POST /api/v1/invoicing/reverse/grade/{gradeId}/{term}?academicYear=2026`

- Path params `gradeId` and `term`. `academicYear` optional (defaults to current year). No body.
- Same bulk behaviour and summary `entity` shape as C, scoped to the given grade.

## Error handling
- `404` — invoice (or student's invoice for that term) not found. Show `message`.
- `409` — the invoice has payments recorded (`amountPaid > 0`) and cannot be reversed; reverse/refund the payment first. Show `message`; do not offer a force option.
- `400` — missing required path/params.
- `500` — unexpected; show `message`.

## Invoice lists exclude reversed invoices
All invoice-listing endpoints (`get-all-invoives`, `get-invoice-by-student/{id}`, `get-current-terms`, `term/{term}`) return only active invoices — reversed/deleted ones (marked `isDeleted = 'Y'`, or removed on reversal) are excluded. To show reversed items, use `GET /api/v1/invoicing/reversals`.

## What happens on the backend (so the UI can reflect it)
- The invoice is **removed** (voided) — it will no longer appear in invoice lists.
- A reversal record is **saved** and returned by `GET /api/v1/invoicing/reversals` (see above), capturing the invoice snapshot, who reversed it, when, the scope, and how many optional fees were released.
- The student's `Finance` for that term/year is reset (its fee total for this invoice goes to zero; `paidAmount` is preserved).
- Optional-fee assignments that were invoiced are flipped back to `invoiced = false` and remain **active** — they'll be included again the next time you generate an invoice for that term.
- The term/year is freed, so `POST /api/v1/invoicing/create/{studentId}/{term}` can generate a fresh invoice.

## What to build
1. **API client**: add `reverseInvoice(invoiceId)` → `POST /invoicing/reverse/{invoiceId}`, `reverseStudentInvoice(studentId, term, academicYear?)` → `POST /invoicing/reverse/student/{studentId}/{term}`, `reverseAllInvoices(term, academicYear?)` → `POST /invoicing/reverse-all/{term}`, and `reverseGradeInvoices(gradeId, term, academicYear?)` → `POST /invoicing/reverse/grade/{gradeId}/{term}`.
2. **Reverse action** on the invoice detail/list row (visible only with `invoice:reverse`):
   - Show a confirmation dialog explaining it will void the invoice and release its optional fees.
   - On success (`200`): show the `message`, remove the invoice from the list (or mark it reversed), and refresh the student's Finance/balance view and the optional-fees list (rows return to `invoiced = false`).
   - On `409`: show the payment-blocked `message`; keep the invoice as-is.
3. **Disable the reverse control** when the invoice has `amountPaid > 0` (the backend will also reject it, but disabling avoids a wasted round-trip).
4. **Re-invoice affordance**: after a successful reversal, the "Generate Invoice" action for that student/term should be available again.
5. **Bulk reverse actions** (visible only with `invoice:reverse`):
   - "Reverse all for term" and "Reverse grade for term" controls, each behind a confirmation dialog that states it voids many invoices and releases their optional fees, and that invoices with payments are skipped.
   - On success (`200`): render the summary — reversed/failed/skipped counts — and surface the `failed[]` reasons (payment-blocked ones) so the user can handle them individually. Refresh the invoice list and affected balances.

## Acceptance checks
- Reversing an unpaid invoice returns `200`, removes it from the list, resets the student's balance for that term, and flips its optional fees back to un-invoiced.
- Attempting to reverse an invoice with a payment returns `409` and the invoice is unchanged.
- After reversal, generating the invoice again for the same student/term succeeds (no "already exists" error) and re-includes the released optional fees.
- The reverse control is hidden for users without `invoice:reverse`.
