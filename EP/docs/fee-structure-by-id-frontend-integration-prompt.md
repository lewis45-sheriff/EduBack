# Frontend Integration Prompt — Get Fee Structure by ID

The frontend already has a fee-structure implementation (list / create / update / delete). This task is to wire in the existing "get one fee structure by id" endpoint where a single structure's full detail is needed (detail and edit views). Do not rebuild the fee-structure module; reuse the existing API client, models, and screens. Only add the by-id fetch and consume its response shape.

## Endpoint

`GET /api/v1/fee-structure/get-fee-structure/{id}`

- Auth: existing bearer JWT. Tenant is resolved server-side.
- No request body and no query params. `{id}` is the fee structure's numeric id.

### Response envelope

All responses use the standard wrapper:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Drive UI state from `statusCode` (same value as the HTTP status). Show `message` on errors. `entity` is `null` on errors.

### Success `200` — `entity` (grouped by term)
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
        { "id": 102, "name": "Lunch",   "amount": 3000.0 }
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

Shape notes:
- `mode` is a string: `"DAY"` or `"BOARDING"`.
- `terms` is **grouped** — each entry has a `term` (`TERM_1` | `TERM_2` | `TERM_3`) and its own `feeItems`. This is different from the flat `feeItems` array returned by the list endpoint (`get-all-fee-structures`). The by-id endpoint nests items under terms, so map to this shape; do not assume the flat one.
- `amount` is a number (double). Render with the app's existing currency formatter.
- `createdOn` / `updatedOn` are local date-times (both currently reflect the last posted date).

### Error statuses
- `404` — no fee structure with that id (also covers a structure that belongs to another tenant). Show `message`; route back to the list or render a not-found state.
- `500` — unexpected error; show `message`.

## What to do in the existing frontend

1. Add a client method next to the current fee-structure API calls, e.g. `getFeeStructureById(id)`, that calls `GET /api/v1/fee-structure/get-fee-structure/{id}` and returns `entity`.
2. Add or point to a type/model for the grouped response:
   `{ id, grade, mode, createdOn, updatedOn, terms: [{ term, feeItems: [{ id, name, amount }] }] }`.
3. Use it in the fee-structure detail / edit view: fetch by id when the view opens, render each term as a section with its fee items, and show `grade` and `mode`.
4. If the existing edit form expects the flat / get-all shape, add a small adapter that flattens `terms[].feeItems` (carrying each item's `term` onto the item) so the current form keeps working without a rewrite.
5. Use the existing shared error handler for `404` / `500` (toast with `message`), and show a loading state while fetching.

## Acceptance checks
- Opening a fee structure's detail / edit view calls the by-id endpoint and renders every term with its items and amounts, plus `grade` and `mode`.
- Requesting a non-existent or other-tenant id shows the `404` message and does not render stale data.
- No regression to the existing list / create / update / delete flows.
