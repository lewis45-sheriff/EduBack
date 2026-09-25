# Manual Finance Transaction — Front-End Integration Prompt

Covers the changes to **creating a finance transaction manually**: the endpoint is now
permission-gated, accepts an optional **supporting document upload** (PNG/JPG/PDF/Word), and
may run through an optional **maker-checker approval** flow controlled by a backend flag.

> ⚠️ **Breaking change for existing code:** the create endpoint no longer accepts a JSON body.
> It now accepts **`multipart/form-data`** with a JSON `data` part plus an optional
> `attachment` file part. Any existing frontend call that posts JSON to this endpoint must be
> migrated (see §2).

---

## 1. Permissions

| Permission | Gates |
|-----------|-------|
| `transaction:create` | Create a manual transaction; view the pending list |
| `transaction:approve` | Approve / reject a pending transaction (checker) |

- Show the "Add transaction" action only to users with `transaction:create`.
- Show Approve/Reject only to users with `transaction:approve`.

---

## 2. Create endpoint (now multipart) — MIGRATE the existing call

```
POST /api/v1/finance-transactions/create-transaction/{studentId}
Content-Type: multipart/form-data
```

Two parts:
- **`data`** — the transaction JSON (same fields you already send).
- **`attachment`** — *optional* file: `png, jpg, jpeg, pdf, doc, docx`, max **10 MB**.

`data` JSON fields (unchanged from before, plus the file moves out to its own part):
```json
{
  "transactionType": "INCOME",          // or EXPENSE
  "category": "School Fees",
  "amount": 2800.00,
  "transactionDate": "2026-09-24",       // yyyy-MM-dd
  "description": "Cash payment at office",
  "paymentMethod": "CASH",               // CASH | BANK | CHEQUE | CARD | OTHER ... (not MPESA for manual)
  "reference": "RCP-0001",               // optional; auto-generated if blank
  "term": "TERM_3",
  "year": 2026,
  "invoiceId": 88
}
```
Do **not** send `source` — the backend forces manual entries to `MANUAL` so they cannot be
disguised as gateway payments.

### Before (JSON) → After (multipart)

**Before:**
```js
await api.post(`/api/v1/finance-transactions/create-transaction/${studentId}`, dto);
```

**After:**
```js
const form = new FormData();
// The data part must be sent as JSON. Use a Blob so its content-type is application/json.
form.append("data", new Blob([JSON.stringify(dto)], { type: "application/json" }));
if (file) form.append("attachment", file); // optional File from <input type="file">

await api.post(
  `/api/v1/finance-transactions/create-transaction/${studentId}`,
  form
  // NOTE: do NOT set Content-Type manually; let the browser set the multipart boundary.
);
```

> Common pitfalls:
> - Sending the `data` part as a plain string makes Spring reject it — wrap it in a `Blob`
>   with `type: "application/json"` (as above), or configure your HTTP client to do so.
> - Do **not** hand-set the `Content-Type` header; the browser adds the multipart boundary.
> - The file input should accept only the allowed types:
>   `accept=".png,.jpg,.jpeg,.pdf,.doc,.docx"`.

### Responses
Standard wrapper `{ message, statusCode, entity }`.
- **Maker-checker OFF (default):** 201 and the transaction is posted immediately (invoice +
  balances updated), same effect as today. `entity` contains the transaction/invoice/finance.
- **Maker-checker ON:** 201 with message *"Transaction submitted and pending approval. No
  balances changed."* and `entity` = the pending record. The balance does **not** change yet.
- Errors: 400 (validation / bad file type / file too large), 403 (missing `transaction:create`),
  500. Surface `message`.

Because the same endpoint can behave either way depending on a backend flag, the UI should
read the returned `message`/`statusCode` and not assume the balance changed. If your app shows
the updated balance after posting, refresh it from the server rather than computing locally.

---

## 3. Supporting document — display & rules

- Allowed: `png, jpg, jpeg, pdf, doc, docx`; max 10 MB. Validate client-side before upload and
  show a friendly error, but the server enforces it too (400 on violation).
- On success, the transaction carries an `attachmentUrl` (a path like
  `/uploads/transaction-attachments/<uuid>.pdf`). Prefix it with your API/base host to build a
  link. Render it as a "View attachment" link (PDF/image open in a new tab; doc/docx download).
- The attachment is optional — the form must submit fine without a file.

---

## 4. Maker-checker (only visible when the backend flag is ON)

If `finance.transaction.maker-checker.enabled=true`, manual transactions become pending and
need a second user to approve. The endpoints:

| Method & path | Permission | Purpose |
|---------------|-----------|---------|
| `GET /api/v1/finance-transactions/pending?status=PENDING_APPROVAL` | create or approve | List pending (filter `PENDING_APPROVAL` / `APPROVED` / `REJECTED`) |
| `PUT /api/v1/finance-transactions/pending/{pendingId}/approve` | `transaction:approve` | Approve → posts the transaction |
| `PUT /api/v1/finance-transactions/pending/{pendingId}/reject?reason=...` | `transaction:approve` | Reject → nothing posted, attachment removed |

Pending record (`entity`) includes: `id`, `studentId`, `studentName`, `admissionNumber`,
`transactionType`, `category`, `amount`, `transactionDate`, `description`, `paymentMethod`,
`reference`, `term`, `year`, `invoiceId`, `attachmentUrl`, `status`, `createdByName`(maker),
`createdAt`, `approvedByName`, `approvedAt`, `rejectionReason`, `postedTransactionId`.

UI guidance:
- Add a "Pending transactions" screen (a checker inbox) listing `GET .../pending` with a status
  filter, defaulting to `PENDING_APPROVAL`.
- Each pending row shows student, amount, type, maker, date, the attachment link, and — for
  `transaction:approve` users — **Approve** / **Reject** buttons.
- The backend blocks a maker from approving their own request (HTTP 409); hide/disable Approve
  where `createdByName` is the current user, and handle 409 with its message.
- After approve/reject, refresh the list and the affected student's balances.
- If the flag is OFF, this screen has no data (creates post immediately). You can keep the menu
  item hidden unless there are pending items, or gate it behind a config your app already reads.

---

## 5. Front-end prompt (paste into your front-end assistant)

> Update the existing **Add / record finance transaction** feature.
>
> 1. **Permission:** only show the "Add transaction" action to users with `transaction:create`.
>
> 2. **Migrate the create call to multipart.** The endpoint
>    `POST /api/v1/finance-transactions/create-transaction/{studentId}` now expects
>    `multipart/form-data`, not JSON:
>    - Build a `FormData` with a `data` part = the transaction JSON wrapped in a
>      `Blob([...], { type: "application/json" })`, and an optional `attachment` part = the
>      selected `File`.
>    - Do not set the `Content-Type` header manually (let the browser set the boundary).
>    - Keep sending the same transaction fields as before; remove any `source` field.
>
> 3. **Add an optional file input** to the transaction form accepting
>    `.png,.jpg,.jpeg,.pdf,.doc,.docx`, max 10 MB. Validate type/size client-side; the form must
>    still submit with no file. Show upload progress/disabled state while posting.
>
> 4. **Handle the response** `{ message, statusCode, entity }`:
>    - On 201 read `message`. It may be an immediate post OR "pending approval" (depends on a
>      backend flag). Do not assume the balance changed; refetch balances from the server.
>    - On 400/403/500 show `message` as an error toast.
>
> 5. **Show the attachment** when present: the transaction has `attachmentUrl`
>    (`/uploads/transaction-attachments/...`). Render a "View attachment" link (prefix with the
>    API host).
>
> 6. **Pending approvals (checker inbox), shown to `transaction:approve` users:**
>    - `GET /api/v1/finance-transactions/pending?status=PENDING_APPROVAL` — list.
>    - `PUT /api/v1/finance-transactions/pending/{id}/approve` — approve (posts it).
>    - `PUT /api/v1/finance-transactions/pending/{id}/reject?reason=...` — reject.
>    - Hide Approve on rows the current user created (backend returns 409 for self-approval;
>      handle that message). Refresh balances/list after each action.
