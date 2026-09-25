# Frontend Rework Prompt — Optional Fees (Fee-Structure-Driven Model)

The optional-fees backend contract has changed. The previous frontend was built against a **catalog-based** model where you assigned a global fee component (`feeComponentId`). That model is gone. Optional fees are now **driven by fee-structure line items**: you assign a specific line item of a fee structure (`feeComponentConfigId`), and its amount is snapshotted from the fee structure.

Rework the existing optional-fees UI and API client to match the new contract below. Reuse the app's existing HTTP client, auth interceptor, error handling, and design system. Do not build a parallel module.

## What changed vs the old implementation (migrate these)

- The assign payload field `feeComponentId` → **`feeComponentConfigId`** (the id of a fee-structure line item, called `configId` in the components list).
- The "which items are optional" source is no longer the global fee-components catalog. It is now the **components of a chosen fee structure**, each carrying `optional` / `parentAssignable` flags and an `amount`.
- The assignment response no longer has `type` / `category` / `feeComponentId`. It now has `feeComponentConfigId`, `feeComponentName`, `feeStructureId`.
- Remove any code that fetched the global catalog to populate the assign dialog. Replace with the fee-structure components fetch.
- Fix the `formatMoney` crash (`Cannot read properties of undefined (reading 'toLocaleString')`) — see the guard below.

## Conventions

- Auth: existing bearer JWT on every request. Tenant, actor, and amount are resolved server-side. Never send `tenantId`, `assignedByUserId`, `assignedBy`, or `amount`.
- Response envelope (all endpoints):
  ```json
  { "message": "string", "statusCode": 200, "entity": <payload-or-null> }
  ```
  Drive UI from `statusCode`; show `message` on errors; `entity` is `null` on errors.
- `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`. `academicYear` is a 4-digit number.
- Money is a number (double); may be `0` or missing — always guard before formatting.

## Permissions (gate UI)

- `optional_fee:assign` — show/enable the assign action.
- `optional_fee:remove` — show/enable remove.
- `optional_fee:read` — show the student's optional-fees list.

Gating is convenience only; the backend enforces the real check plus a guardian check for parents. Always handle `403`.

---

## Endpoint 1 — List assignable line items for a fee structure

`GET /api/v1/fee-structure/get-fee-structure-components/{feeStructureId}`

Success `200`, `entity` is a flat array:
```json
[
  { "configId": 101, "name": "Tuition",  "amount": 10000.0, "term": "TERM_1", "optional": false, "parentAssignable": false },
  { "configId": 102, "name": "Swimming", "amount": 5000.0,  "term": "TERM_2", "optional": true,  "parentAssignable": true  },
  { "configId": 103, "name": "Trip",     "amount": 3000.0,  "term": "TERM_2", "optional": true,  "parentAssignable": false }
]
```
- `configId` — the line-item id you send as `feeComponentConfigId` when assigning. Use as list key.
- Offer only rows where `optional === true`. For **parent** users, offer only rows where `optional === true && parentAssignable === true`.
- Errors: `404` fee structure not found; `500` unexpected.

## Endpoint 2 — Assign an optional fee

`POST /api/v1/optional-fees/`

Request body:
```json
{
  "studentId": 123,
  "feeComponentConfigId": 102,
  "term": "TERM_1",
  "academicYear": 2026
}
```
- `feeComponentConfigId` = selected row's `configId`.
- `term` optional; if omitted the server uses the line item's own term; if provided it must match the line item's term.
- `academicYear` optional; defaults to current year.

Success `201`, `entity`:
```json
{
  "id": 99,
  "studentId": 123,
  "studentName": "Ann Doe",
  "feeComponentConfigId": 102,
  "feeComponentName": "Swimming",
  "feeStructureId": 12,
  "amount": 5000,
  "term": "TERM_2",
  "academicYear": 2026,
  "assignedBy": "ADMIN",
  "assignedAt": "2026-09-24T10:15:30",
  "status": "ACTIVE",
  "invoiced": false
}
```

Errors (show `message`):
- `400` — missing `studentId`/`feeComponentConfigId`, term mismatch, or the line item is mandatory (`optional = false`).
- `403` — parent not linked to the student, or line item not `parentAssignable`.
- `404` — student or line item not found (also other-tenant), or its fee structure is deleted.
- `409` — an active assignment for the same student + line item + term + year already exists.

## Endpoint 3 — Remove an optional fee (soft delete)

`DELETE /api/v1/optional-fees/{id}`

- Success `200` (`entity` null).
- `409` — already included in a generated invoice; cannot be removed. Disable the remove control when `invoiced === true`.
- `404` — no active assignment; `403` — parent not authorized.

## Endpoint 4 — List a student's optional fees

`GET /api/v1/optional-fees/student/{studentId}?term=TERM_1&academicYear=2026`

- `term`/`academicYear` optional; omit both for all active assignments.
- Success `200`, `entity` is an array of the assignment shape from Endpoint 2.
- `403` — parent requesting a non-child student; handle cleanly, don't render data.

---

## Rework steps

1. **API client**: rename/replace `assignOptionalFee` to send `{ studentId, feeComponentConfigId, term?, academicYear? }`. Add `getFeeStructureComponents(feeStructureId)`. Keep `removeOptionalFee(id)` and `listStudentOptionalFees(studentId, term?, academicYear?)`.
2. **Types/models**: update the assignment model to `{ id, studentId, studentName, feeComponentConfigId, feeComponentName, feeStructureId, amount, term, academicYear, assignedBy, assignedAt, status, invoiced }`. Add a line-item model `{ configId, name, amount, term, optional, parentAssignable }`. Delete the old catalog-based fields (`feeComponentId`, `type`, `category`).
3. **Assign dialog (`AssignOptionalFeeDialog.tsx`)**:
   - Require a fee structure context (the student's structure, or a selected one). Fetch its components with Endpoint 1.
   - Populate the picker with assignable rows only (filter by `optional`, and `parentAssignable` for parents).
   - Submit `feeComponentConfigId = row.configId`. Do not send `amount`.
   - Apply the money guard below to stop the current crash.
4. **Student optional-fees list**: render assignments from Endpoint 4 with name, term, amount, source (`assignedBy`), assigned date, status, and an "Invoiced" indicator. Disable "Remove" when `invoiced`.
5. **Error handling**: route `400/403/404/409/500` through the shared handler using `message`; refresh the list on success.
6. **Parent portal variant**: scope to the parent's own children; expect `403` for non-child ids and handle without exposing data.

## Money guard (fixes the runtime crash)

```tsx
const formatMoney = (value?: number | null) => {
  const n = Number(value);
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString(undefined, {
    style: "currency",
    currency: "KES", // match the app's currency
    minimumFractionDigits: 2,
  });
};

// picker — assignable rows only
{components
  .filter((c) => c.optional /* && (isParent ? c.parentAssignable : true) */)
  .map((c) => (
    <option key={c.configId} value={c.configId}>
      {c.name} · {c.term} {c.amount != null ? `– ${formatMoney(c.amount)}` : ""}
    </option>
  ))}
```

## Optional: configuring which line items are optional

If you own the fee-structure editor, the create/update fee-item payload now accepts two booleans (both default `false`, so existing structures stay mandatory):
```json
{ "name": "Swimming", "amount": 5000, "optional": true, "parentAssignable": true }
```
Expose these as per-item toggles in the editor.

## Acceptance checks

- The assign dialog lists a fee structure's components; only `optional` rows are assignable (and only `parentAssignable` for parents).
- Assigning sends `feeComponentConfigId` and succeeds; the created assignment's `amount` matches the line item's amount.
- Assigning the same line item twice for the same term/year shows the `409` message, no duplicate row.
- Removing an un-invoiced assignment removes it; an invoiced one is blocked (`409`) and its Remove control is disabled.
- No `toLocaleString` crash when an amount is missing (shows `—`).
- A parent cannot view/assign/remove for a non-child student (`403` handled cleanly).
- No leftover references to the old `feeComponentId` / catalog-based assign flow.
