# Frontend Integration Prompt — Fee Structure Components (for Optional-Fee Assignment)

The frontend already has fee-structure and optional-fee screens. This task wires in an endpoint that returns the fee components (line items, with their per-term amounts and optional flags) belonging to a fee structure, so the "Assign Optional Fee" dialog can let the user pick a line item and reuse its amount. Do not rebuild existing screens; reuse the existing API client, models, and dialog. Only add this fetch and use its response.

## Background

Optional fees are driven by **fee-structure line items** (`FeeComponentConfig`), not the global catalog. Each line item carries its per-term `amount` and two flags:
- `optional` — the item may be assigned to a student as an optional fee.
- `parentAssignable` — a parent may self-assign it (only meaningful when `optional` is true).

The optional-fee assign endpoint expects the line item's id as `feeComponentConfigId`. This endpoint gives you that id (`configId`) plus the flags, so the dialog can show only assignable rows.

## Endpoint

`GET /api/v1/fee-structure/get-fee-structure-components/{id}`

- Auth: existing bearer JWT. Tenant resolved server-side.
- No body, no query params. `{id}` is the fee structure's numeric id.

### Response envelope
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```

### Success `200` — `entity` is a flat array
```json
[
  { "configId": 101, "name": "Tuition",  "amount": 10000.0, "term": "TERM_1", "optional": false, "parentAssignable": false },
  { "configId": 102, "name": "Swimming", "amount": 5000.0,  "term": "TERM_2", "optional": true,  "parentAssignable": true  },
  { "configId": 103, "name": "Trip",     "amount": 3000.0,  "term": "TERM_2", "optional": true,  "parentAssignable": false }
]
```

Field notes:
- `configId` — id of the fee-structure line item. This is what you send as `feeComponentConfigId` when assigning an optional fee. Use it as the React list key.
- `name` — line item name.
- `amount` — number (double); may be `0`. Always guard before formatting (see below).
- `term` — `TERM_1` | `TERM_2` | `TERM_3`.
- `optional` — only rows with `optional === true` may be assigned as optional fees.
- `parentAssignable` — for parent users, only offer rows where both `optional` and `parentAssignable` are true.

### Error statuses
- `404` — fee structure not found (or other-tenant id). Show `message`.
- `500` — unexpected error; show `message`.

## What to do in the existing frontend

1. Add a client method next to the fee-structure calls, e.g. `getFeeStructureComponents(feeStructureId)`, calling `GET /api/v1/fee-structure/get-fee-structure-components/{id}` and returning `entity` (default `[]` if null).
2. In the "Assign Optional Fee" dialog, when a fee structure is selected, fetch its components and show only assignable rows:
   - Admin users: rows where `optional === true`.
   - Parent users: rows where `optional === true && parentAssignable === true`.
   For each option show `name`, `term`, and formatted `amount`.
3. On submit, send the optional-fee assignment with `feeComponentConfigId = row.configId` (plus `studentId`, and optionally `term`/`academicYear`). Do not send `amount` — the server snapshots the line item's amount.
4. Handle `404`/`500` via the shared error handler; show a loading state while fetching.

## Guard money formatting (fixes the current crash)

`AssignOptionalFeeDialog.tsx` throws `Cannot read properties of undefined (reading 'toLocaleString')` because `formatMoney` is called with an undefined amount. Make the formatter null-safe and guard the call site:

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

// call site — only assignable rows are shown
{components
  .filter((c) => c.optional /* && (isParent ? c.parentAssignable : true) */)
  .map((c) => (
    <option key={c.configId} value={c.configId}>
      {c.name} · {c.term} {c.amount != null ? `– ${formatMoney(c.amount)}` : ""}
    </option>
  ))}
```

## Configuring which items are optional

Line items are flagged when a fee structure is created/updated. The fee-structure create/update payload's fee items now accept two optional booleans:
```json
{ "name": "Swimming", "amount": 5000, "optional": true, "parentAssignable": true }
```
Both default to `false` when omitted, so existing structures stay fully mandatory. If you build a fee-structure editor, expose these two toggles per fee item.

## Acceptance checks
- Selecting a fee structure loads its line items with names, terms, amounts, and flags.
- Only `optional` rows are offered for assignment (and only `parentAssignable` ones for parents).
- Choosing a row and confirming assigns the optional fee using `configId` as `feeComponentConfigId`; the created assignment's amount matches the line item's `amount`.
- No crash when an amount is missing (shows `—`).
- `404`/`500` are surfaced via the shared error handler with the returned `message`.
