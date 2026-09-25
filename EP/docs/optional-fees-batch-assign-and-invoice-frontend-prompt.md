# Frontend Prompt — Multi-Select Optional Fees + Invoicing

Two capabilities to wire into the existing optional-fees screens: (1) assign **multiple** optional fee line items to a student in one call, and (2) **invoice** the assigned optionals (which happens through the existing invoice-create flow — no separate "invoice optionals" call). Reuse the app's existing HTTP client, auth interceptor, error handling, and design system.

All requests use port **8085** (backend), bearer JWT, and the standard envelope:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Never send `tenantId`, `assignedByUserId`, `assignedBy`, or `amount` — the server resolves them.

---

## 1. Get selectable optional line items (multi-select source)

`GET /api/v1/fee-structure/get-fee-structure-components/{feeStructureId}`

`entity` is a flat array; each row:
```json
{ "configId": 259, "name": "Activity Fee", "amount": 1500.00, "term": "TERM_1", "optional": true, "parentAssignable": false }
```
- Show only rows where `optional === true` as selectable. For parent users, also require `parentAssignable === true`.
- Use `configId` as the checkbox value/key. Render a multi-select list (checkboxes) grouped by `term`, showing name + formatted amount.

Guard money formatting (avoids the `toLocaleString` crash):
```tsx
const formatMoney = (v?: number | null) => {
  const n = Number(v);
  return Number.isFinite(n)
    ? n.toLocaleString(undefined, { style: "currency", currency: "KES", minimumFractionDigits: 2 })
    : "—";
};
```

## 2. Batch-assign the selected line items

`POST /api/v1/optional-fees/batch`  · permission `optional_fee:assign`

Request body:
```json
{
  "studentId": 123,
  "feeComponentConfigIds": [259, 262, 265],
  "term": "TERM_1",
  "academicYear": 2026
}
```
- `feeComponentConfigIds` — the `configId`s the user checked.
- `term` optional; if omitted each line item's own term is used; if provided it must match each selected item's term.
- `academicYear` optional; defaults to the current year.

Response is **partial-success aware**:
- `201` — all assigned.
- `200` — some assigned, some failed (e.g. a duplicate).
- `400` — none assigned.

`entity` shape (always present on 200/201/400):
```json
{
  "requested": 3,
  "assignedCount": 2,
  "failedCount": 1,
  "assigned": [
    { "id": 99, "studentId": 123, "feeComponentConfigId": 259, "feeComponentName": "Activity Fee", "feeStructureId": 15, "amount": 1500.00, "term": "TERM_1", "academicYear": 2026, "assignedBy": "ADMIN", "assignedAt": "2026-09-24T10:15:30", "status": "ACTIVE", "invoiced": false }
  ],
  "failed": [
    { "feeComponentConfigId": 262, "statusCode": 409, "message": "An active 'Activity Fee' optional fee already exists ..." }
  ]
}
```

Frontend handling:
- On `201`, show success and refresh the student's optional-fee list.
- On `200`, show a partial-success notice listing the failed items using each `failed[].message` (a per-row toast or an inline summary). Keep the succeeded ones.
- On `400`, show the failures; nothing was created.
- Individual failures do not roll back the successes — reflect exactly what `assigned` / `failed` report.

### Single assign still exists
`POST /api/v1/optional-fees/` with `{ studentId, feeComponentConfigId, term?, academicYear? }` for one item. Prefer the batch endpoint when the UI is a multi-select.

## 3. List a student's assigned optionals

`GET /api/v1/optional-fees/student/{studentId}?term=TERM_1&academicYear=2026`  · permission `optional_fee:read`

`entity` is an array of the assignment shape shown under `assigned` above. Use it to render the student's current optional fees (name, term, amount, `assignedBy`, `assignedAt`, `status`, `invoiced`). Disable "Remove" when `invoiced === true`.

## 4. Remove an assignment

`DELETE /api/v1/optional-fees/{id}`  · permission `optional_fee:remove`
- `200` removed. If the assignment had already been included in a generated invoice, its amount is **automatically deducted** from that invoice's total and balance (and the Finance rollup); `amountPaid` is never changed. The `message` indicates when a deduction happened.
- `404` not found; `403` not authorized.
- After a successful remove, refresh both the optional-fee list and the invoice view so the reduced total/balance shows.

---

## 5. Invoicing the assigned optionals

There is **no separate "invoice optionals" endpoint**. Optional fees are folded into the normal invoice automatically. Assign first (steps 2), then generate the invoice:

`POST /api/v1/invoicing/create/{studentId}/{term}`  · permission `invoice:create`

When an invoice is created for a student/term/year, the backend:
- sums the mandatory fee-structure items for the term,
- adds every **active** optional fee assigned to that student for the same term/year,
- adds the carried-forward balance,
- marks those optional assignments as `invoiced = true` (so they are counted once and can no longer be removed).

The invoice response now includes a breakdown — render it:
```json
{
  "invoiceId": 1000,
  "studentName": "Ann Doe",
  "grade": "Grade 6",
  "feeMode": "DAY",
  "term": "TERM_1",
  "academicYear": 2026,
  "mandatoryFeesAmount": 29500.00,
  "optionalFeesAmount": 1500.00,
  "carriedForwardAmount": 0,
  "totalAmount": 31000.00,
  "amountPaid": 0,
  "balance": 31000.00,
  "status": "P",
  "invoiceDate": "2026-09-24",
  "dueDate": "2026-10-24"
}
```

Recommended UX flow on the student billing screen:
1. Open the assign dialog → multi-select optional line items → `POST /optional-fees/batch`.
2. Show the updated optional-fee list (step 3).
3. Click "Generate Invoice" → `POST /api/v1/invoicing/create/{studentId}/{term}`.
4. Display the returned breakdown; the optional rows now show `invoiced = true`.

Notes / gotchas:
- Invoicing only works for the **current term**; a past/future term returns an error `message`.
- Re-creating an invoice for the same student/term/year is rejected (`message` "already exists") — do not auto-retry; optional fees are never double-counted.
- Bulk invoicing (`POST /api/v1/invoicing/invoice-all?term=TERM_1`) applies the same optional-fee logic to every student.

## Acceptance checks
- Selecting several optional line items and submitting assigns them in one call; the list reflects all successes.
- A mix of new + duplicate selections returns `200` with a clear per-item failure list; successes are kept.
- Generating the invoice adds the assigned optionals to `optionalFeesAmount` and the total, and flips those rows to `invoiced = true`.
- Removing an assignment that was already invoiced deducts its amount from the invoice total and balance (invoice view reflects the drop); `amountPaid` is unchanged.
- No `toLocaleString` crash when an amount is missing.
