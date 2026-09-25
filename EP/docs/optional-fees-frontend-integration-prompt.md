# Frontend Integration Prompt — Optional Fees & Student-Type Invoicing

You are integrating a frontend with the EduePoa backend. Two billing capabilities are now available: (1) hardened student-type invoicing that exposes the billing mode (DAY/BOARDING) on invoice responses, and (2) optional fee items that authorized school staff — or, where permitted, parents — can assign to individual students. Build the UI and API client for these against the contracts below. Do not invent endpoints or fields that are not listed here.

## Conventions (match the existing app)

- Base URL: same host/prefix the app already uses. Paths below are absolute from that base.
- Auth: send the existing bearer JWT on every request. The backend resolves the user, tenant, and actor server-side. Never send `tenantId`, `assignedByUserId`, `assignedBy`, or `amount` in request bodies.
- All responses use the standard envelope:
  ```json
  { "message": "string", "statusCode": 200, "entity": <payload-or-null> }
  ```
  Drive UI state from `statusCode` (HTTP status is the same value). Show `message` on errors. `entity` is `null` on errors.
- Money is a decimal number (e.g. `5000` or `5000.00`); render with the app's existing currency formatter.
- `term` is one of `TERM_1`, `TERM_2`, `TERM_3`. `academicYear` is a 4-digit year (e.g. `2026`).

## Permissions (gate UI on these)

The current user's role carries a set of permission strings. Show/hide controls accordingly:

- `optional_fee:assign` — can open the assign dialog and submit assignments.
- `optional_fee:remove` — can remove an assignment.
- `optional_fee:read` — can view a student's optional fees.
- `optional_fee:manage_catalog` — can edit which catalog items are `optional` / `parentAssignable` (only if you build a catalog admin screen).

UI gating is convenience only; the backend enforces the real check plus a guardian-relationship check for parents. Always handle `403` gracefully.

---

## 1. Optional Fees API

### 1.1 Assign an optional fee
`POST /api/v1/optional-fees/`

The optional item is a **fee-structure line item** (`FeeComponentConfig`), identified by `feeComponentConfigId`. Get the assignable line items (with their amounts and `optional`/`parentAssignable` flags) from the fee-structure components endpoint — see `fee-structure-components-frontend-integration-prompt.md`.

Request body (only these fields):
```json
{
  "studentId": 123,
  "feeComponentConfigId": 101,
  "term": "TERM_1",
  "academicYear": 2026
}
```
- `feeComponentConfigId` is the `configId` of the fee-structure line item you selected.
- `term` is optional. When omitted, the line item's own term is used. When provided it must match the line item's term (otherwise `400`).
- `academicYear` defaults to the current year when omitted.

Success `201`, `entity`:
```json
{
  "id": 99,
  "studentId": 123,
  "studentName": "Ann Doe",
  "feeComponentConfigId": 101,
  "feeComponentName": "Swimming",
  "feeStructureId": 12,
  "amount": 5000,
  "term": "TERM_1",
  "academicYear": 2026,
  "assignedBy": "ADMIN",
  "assignedAt": "2026-09-24T10:15:30",
  "status": "ACTIVE",
  "invoiced": false
}
```

Error statuses to handle with the returned `message`:
- `400` — missing `studentId`/`feeComponentConfigId`, invalid/blank line-item term, requested `term` does not match the line item's term, or the line item is mandatory (`optional = false`).
- `403` — parent not linked to the student, or the line item is not `parentAssignable`.
- `404` — student or fee-structure line item not found (also covers cross-tenant ids), or its fee structure is deleted.
- `409` — an active assignment for the same student + line item + term + year already exists.

### 1.2 Remove an optional fee (soft delete)
`DELETE /api/v1/optional-fees/{id}`

Success `200` (`entity` is `null`).

Errors:
- `404` — no active assignment with that id.
- `409` — the assignment has already been included in a generated invoice and cannot be removed (show `message`, disable the remove button when `invoiced === true`).
- `403` — parent removing an assignment they are not authorized for.

### 1.3 List a student's optional fees
`GET /api/v1/optional-fees/student/{studentId}?term=TERM_1&academicYear=2026`

- `term` and `academicYear` are optional query params. Omit both to list all active assignments for the student across terms/years.
- Success `200`, `entity` is an array of the assignment object shape shown in 1.1.
- `403` — a parent requesting a student who is not their child. Do not expose data; show an access message.

---

## 2. Student-Type Invoicing (updated response shape)

The existing invoicing endpoints are unchanged in path/behavior. The invoice response DTO now carries additional fields. Update your invoice models/renderers to read them; treat any as possibly `null` on older records.

New/updated fields on the invoice response object:
- `feeMode` — `"DAY"` or `"BOARDING"`. Show this as the student's billing type. (Weekly-boarding students are billed as `BOARDING`.)
- `mandatoryFeesAmount` — fees from the fee structure for the term.
- `optionalFeesAmount` — sum of optional fees applied to the term.
- `carriedForwardAmount` — arrears (positive) or credit (negative) from prior terms.
- `totalAmount` — the invoice total (`mandatory + optional + carriedForward`).

Existing fields remain: `invoiceId`, `studentName`, `admissionNumber`, `grade`, `term`, `academicYear`, `amountPaid`, `balance`, `status` (char: `P` pending, `C` cleared, `O` overdue), `invoiceDate`, `dueDate`.

Endpoints (unchanged):
- `POST /api/v1/invoicing/create/{studentId}/{term}` — create one invoice.
- `POST /api/v1/invoicing/invoice-all?term=TERM_1` — bulk.
- `GET /api/v1/invoicing/get-all-invoives` — all.
- `GET /api/v1/invoicing/get-invoice-by-student/{id}` — by student.
- `GET /api/v1/invoicing/get-current-terms` — current term.
- `GET /api/v1/invoicing/term/{term}` — by term.

Note: an invoice can only be created for the current term; attempting a past/future term returns an error `message`. Creating a second invoice for the same student/term/year is rejected — surface the `message` rather than retrying.

---

## What to build

1. **Invoice detail view**: add a fee breakdown block showing `feeMode` (as a badge), `mandatoryFeesAmount`, `optionalFeesAmount`, `carriedForwardAmount`, and `totalAmount`, then `amountPaid` and `balance`.
2. **Student "Optional Fees" panel** (on the student profile/finance tab):
   - Table of assignments from `GET .../student/{id}` with columns: name, type/category, amount, term, year, source (`assignedBy`), assigned date, status, and an "Invoiced" indicator.
   - "Assign optional fee" action (visible with `optional_fee:assign`) opening a dialog that selects a fee component, term, and year, then `POST`s. Only offer components the school has marked optional; for parent users, only show `parentAssignable` ones.
   - Per-row "Remove" (visible with `optional_fee:remove`), disabled when `invoiced === true`.
3. **Error handling**: a shared handler that maps `400/403/404/409` to a toast using the response `message`, and refreshes the list on success.
4. **Parent portal variant**: same panel scoped to the parent's own children only. Expect `403` if a parent targets a non-child student id — handle it, do not assume the id is valid just because it is known.

## Acceptance checks

- Assigning succeeds and the new row appears with the snapshot `amount`; a later catalog price change must not alter existing rows.
- Assigning the same component twice for the same term/year shows the `409` message, not a duplicate row.
- Removing an un-invoiced assignment removes it from the active list; an invoiced one is blocked with the `409` message.
- Invoice totals visibly equal `mandatory + optional + carriedForward`, and `feeMode` matches the student's boarding type.
- A parent cannot view/assign/remove for a student who is not their child (`403` handled cleanly).

Ask me for the exact base URL/auth header format and the fee-component catalog endpoint if your client needs them; otherwise reuse the app's existing HTTP client and auth interceptor.


---

# Frontend Integration Prompt — Get Fee Structure by ID

The frontend already has a fee-structure implementation (list/create/update/delete). This task is to wire in the existing "get one fee structure by id" endpoint where a single structure's full detail is needed (detail/edit views). Do not rebuild the fee-structure module; reuse the existing API client, models, and screens. Only add the by-id fetch and use its response shape.

## Endpoint

`GET /api/v1/fee-structure/get-fee-structure/{id}`

- Auth: existing bearer JWT. Tenant is resolved server-side.
- No request body or query params. `{id}` is the fee structure's numeric id.

### Responses (standard envelope)
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```

Success `200`, `entity` is a **grouped-by-term** structure:
```json
{
  "id": 12,
  "grade": "Grade 6",
  "mode": "DAY",
  "createdOn": "2026-01-10T09:00:00",
  "updatedOn": "2026-01-10T09:00:00",
  "terms": [
    {
      "term": "TERM_1",
      "feeItems": [
        { "id": 101, "name": "Tuition", "amount": 10000.0 },
        { "id": 102, "name": "Lunch", "amount": 3000.0 }
      ]
    },
    {
      "term": "TERM_2",
      "feeItems": [
        { "id": 103, "name": "Tuition", "amount": 10000.0 }
      ]
    }
  ]
}
```

Notes on the shape:
- `mode` is a string, `"DAY"` or `"BOARDING"`.
- `terms` is grouped: each entry has a `term` (`TERM_1` | `TERM_2` | `TERM_3`) and its `feeItems`. This differs from the flat `feeItems` list used by the list/get-all endpoint (`get-all-fee-structures`) — the by-id endpoint nests items under terms. Map accordingly; do not assume the flat shape.
- `amount` is a number (double). Render with the existing currency formatter.
- `createdOn`/`updatedOn` are local date-times (both currently reflect the last posted date).

### Error statuses
- `404` — no fee structure with that id (also covers a structure from another tenant). Show `message`, route back to the list or show a not-found state.
- `500` — unexpected error; show `message`.

## What to do in the existing frontend

1. Add a client method, e.g. `getFeeStructureById(id)`, next to the existing fee-structure API calls, hitting `GET /api/v1/fee-structure/get-fee-structure/{id}` and returning `entity`.
2. Add/point a model/type for the grouped response: `{ id, grade, mode, createdOn, updatedOn, terms: [{ term, feeItems: [{ id, name, amount }] }] }`.
3. Use it in the fee-structure detail/edit view: fetch by id on open, render terms as sections with their fee items, and show the `grade` and `mode`. If the edit form currently expects the flat/get-all shape, add a small adapter that flattens `terms[].feeItems` (carrying `term` onto each item) so the existing form keeps working.
4. Handle `404`/`500` via the existing shared error handler (toast with `message`), and show a loading state while fetching.

## Acceptance checks
- Opening a fee structure's detail/edit view calls the by-id endpoint and renders every term with its items and amounts, plus grade and mode.
- Requesting a non-existent or other-tenant id shows the `404` message and does not render stale data.
- No regression to the existing list/create/update/delete flows.
