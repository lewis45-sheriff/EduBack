# Frontend Prompt — Reversed Invoices History

Every time an invoice is reversed (voided) — whether a single invoice, a student's invoice, a whole grade, or school-wide — the backend now **saves a record of that operation**. This prompt covers building a "Reversed Invoices" screen that lists those saved operations, and reflects the fact that reversed invoices no longer appear in the normal invoice lists.

Reuse the app's existing HTTP client, auth interceptor, error handling, and design system.

All requests use port **8085** (backend), bearer JWT, and the standard envelope:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Drive UI from `statusCode`; show `message` on errors.

## Permission
`invoice:reverse` — gate the Reversed Invoices screen and its data fetch on this. Users without it should not see the screen.

## Endpoint

### List reversed invoices (reversal history)
`GET /api/v1/invoicing/reversals`

- No params. Returns every saved reversal operation, newest first.
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
- Empty history returns `200` with `entity: []` and a "No reversed invoices found" message.

## Field reference
| Field | Meaning |
|---|---|
| `id` | Id of the reversal record (not the invoice). |
| `originalInvoiceId` | The id the invoice had before it was voided. |
| `studentId`, `studentName`, `admissionNumber`, `grade` | Who the reversed invoice belonged to (snapshot). |
| `term`, `academicYear` | The term/year of the reversed invoice. |
| `totalAmount`, `amountPaid`, `balance` | Invoice figures captured at the moment of reversal. |
| `invoiceDate`, `dueDate` | The reversed invoice's own dates. |
| `scope` | How the reversal was triggered: `SINGLE` (by invoice id), `STUDENT` (by student + term), `GRADE` (bulk per grade), `SCHOOL_WIDE` (bulk per term). |
| `releasedOptionalFees` | How many optional-fee assignments were freed for re-invoicing. |
| `reversedBy` | Email/username of the user who performed the reversal. |
| `reversedAt` | Timestamp of the reversal (ISO local date-time). |

## Reversed invoices are excluded from normal lists
The standard invoice-listing endpoints return only active invoices — reversed/voided ones are excluded:
- `GET /api/v1/invoicing/get-all-invoives`
- `GET /api/v1/invoicing/get-invoice-by-student/{id}`
- `GET /api/v1/invoicing/get-current-terms`
- `GET /api/v1/invoicing/term/{term}`

So a reversed invoice disappears from those views and shows up only in the Reversed Invoices history above. The frontend does not need to filter reversed items out client-side.

## Error handling
- `200` with `entity: []` — no reversals yet; show an empty state.
- `500` — unexpected; show `message`.
- `403` — user lacks `invoice:reverse`; hide the screen (should not reach here if gated).

## What to build
1. **API client**: add `getReversedInvoices()` → `GET /invoicing/reversals`.
2. **Reversed Invoices screen** (visible only with `invoice:reverse`):
   - A table listing the history newest-first: student (name + admission no.), grade, term/year, original invoice id, total/paid/balance, scope, released optional fees, reversed by, and reversed at.
   - Render `scope` as a readable badge (Single / Student / Grade / School-wide).
   - Show `reversedBy` and a formatted `reversedAt`.
   - Client-side search (by student name/admission no.) and filters (term, academic year, scope) over the returned list are optional niceties.
3. **Empty state**: when `entity` is `[]`, show a friendly "No invoices have been reversed" message.
4. **Cross-links** (optional): from a reversal row, offer to re-generate the invoice for that student/term, since reversal frees the term (`POST /api/v1/invoicing/create/{studentId}/{term}`).
5. **Refresh**: after any reversal action elsewhere (single, student, grade, or school-wide), refresh this list so the new record appears.

## Acceptance checks
- After reversing an invoice, it disappears from the normal invoice lists and a new row appears in the Reversed Invoices history with the correct `scope`, `reversedBy`, and `reversedAt`.
- A school-wide or per-grade bulk reversal adds one history row per invoice reversed, each with scope `SCHOOL_WIDE` or `GRADE`.
- The history is ordered newest-first.
- With no reversals, the screen shows an empty state (not an error).
- The screen and its fetch are hidden for users without `invoice:reverse`.
