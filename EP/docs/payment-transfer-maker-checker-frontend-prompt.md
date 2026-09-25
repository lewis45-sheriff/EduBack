# Frontend Prompt — Transfer a Payment Between Students (Maker-Checker)

Add the ability to transfer a recorded payment from one student to another. The maker identifies the source payment by its **unique payment reference** (e.g. an M-Pesa receipt code); the backend derives the source student, source invoice, term, year, payment method, and the original amount from that payment. The maker chooses how much to move (**full or partial**) and the destination student + invoice.

The transfer does **not** touch any invoice or balance when it is created — it is stored as a request and stays `PENDING_APPROVAL` until a different user (the **checker**) approves it. Only on approval does the money move: it is removed from the source student's invoice/finance and applied to the destination student's invoice/finance, and two audit transactions (out/in) are written against the original reference. A checker cannot approve their own request (maker ≠ checker is enforced by the backend).

Reuse the app's existing HTTP client, auth interceptor, error handling, and design system.

All requests use port **8085** (backend), bearer JWT, and the standard envelope:
```json
{ "message": "string", "statusCode": 200, "entity": <payload-or-null> }
```
Drive UI from `statusCode`; show `message` on errors.

## Permissions
- `payment_transfer:create` — initiate (maker) a transfer. Gate the "Transfer payment" control on this.
- `payment_transfer:approve` — approve or reject (checker) a transfer. Gate the approve/reject controls on this.

A user may hold both permissions, but the backend still blocks them from approving a transfer they created. Reflect that in the UI (see below).

## Lifecycle
`PENDING_APPROVAL` → `APPROVED` (money moves) or `REJECTED` (discarded, no financial effect).
- Creating a transfer records intent only — no invoice/balance change.
- Approving applies the transfer to both students' invoices and Finance rollups.
- Rejecting closes the request with a reason; nothing moves.

## Endpoints

### A. Initiate a transfer (maker)
`POST /api/v1/payment-transfers/create`

Permission: `payment_transfer:create`. Body:
```json
{
  "sourcePaymentReference": "SGR7XYZ12K",
  "amount": 2000.00,
  "destStudentId": 22,
  "destInvoiceId": 305,
  "reason": "Parent split payment across two children"
}
```
- `sourcePaymentReference` — the unique reference of an existing INCOME payment (e.g. M-Pesa receipt). The backend looks it up and derives source student, source invoice, term, year, payment method, and original amount.
- `amount` — how much to move. Must be > 0 and ≤ the **transferable** amount for that reference (original payment amount minus what other pending/approved transfers already committed against it). Send the full amount for a full transfer, or less for a partial one.
- `destStudentId` + `destInvoiceId` — where the money goes. The invoice must belong to the destination student. Same term/year as the source (derived).
- `reason` — optional free text.
- Success `201`, `entity` is the created transfer (status `PENDING_APPROVAL`) — see the transfer shape below. **No balances change.**
- The maker does **not** supply amount-per-student, term, method, or the source student — those are derived. Show them back to the maker from the response for confirmation.

### B. Approve a transfer (checker)
`PUT /api/v1/payment-transfers/approve/{id}`

Permission: `payment_transfer:approve`. No body (or an optional `{ "comment": "..." }`).
- Backend enforces the approver is **not** the maker.
- On success the amount is removed from the source invoice/finance and applied to the destination invoice/finance, status becomes `APPROVED`, and `approvedBy`/`approvedAt` are set.
- Success `200`, `entity` is the updated transfer.

### C. Reject a transfer (checker)
`PUT /api/v1/payment-transfers/reject/{id}`

Permission: `payment_transfer:approve`. Body: `{ "reason": "why" }`.
- No financial effect. Status becomes `REJECTED`, `rejectionReason` is stored.
- Success `200`, `entity` is the updated transfer.

### D. List transfers
`GET /api/v1/payment-transfers?status=PENDING_APPROVAL`

- Optional `status` filter (`PENDING_APPROVAL` | `APPROVED` | `REJECTED`).
- Success `200`, `entity` is an array of transfers. Use this to build the approval queue.

### E. Get one transfer
`GET /api/v1/payment-transfers/{id}`
- Success `200`, `entity` is a single transfer.

## Transfer shape (entity)
```json
{
  "id": 45,
  "status": "PENDING_APPROVAL",
  "sourcePaymentReference": "SGR7XYZ12K",
  "sourceTransactionId": 900,
  "originalPaymentAmount": 5000.00,
  "amount": 2000.00,
  "remainingTransferableAfter": 3000.00,
  "sourceStudentId": 10,
  "sourceStudentName": "Ann Doe",
  "sourceInvoiceId": 210,
  "destStudentId": 22,
  "destStudentName": "Jon Kay",
  "destInvoiceId": 305,
  "term": "TERM_1",
  "academicYear": 2026,
  "paymentMethod": "MPESA",
  "reason": "Parent split payment across two children",
  "createdByName": "maker@school.ac.ke",
  "createdAt": "2026-09-25T10:15:00",
  "approvedByName": null,
  "approvedAt": null,
  "appliedAt": null,
  "rejectionReason": null
}
```
- `originalPaymentAmount` — the full amount of the source payment.
- `remainingTransferableAfter` — how much of the payment is still transferable after this request (populated on create; may be null on list/get).
- `appliedAt` — null until approved; set at the moment balances actually move. A clear signal that no money has moved yet.

## Error handling
- `400` — missing/invalid fields (`amount <= 0`, missing `sourcePaymentReference`, missing `destStudentId`/`destInvoiceId`, or source == destination). Show `message`.
- `404` — no payment matches the reference, or the destination student/invoice was not found. Show `message`.
- `409` — the requested `amount` exceeds the transferable amount for the reference; the destination invoice doesn't belong to the destination student; the source payment isn't linked to an invoice; the transfer isn't `PENDING_APPROVAL` (already actioned); **the maker tried to approve their own transfer**; or at approval the amount can no longer be transferred (other transfers consumed it). Show `message`; keep the transfer pending where applicable. Do not offer a retry for self-approval.
- `500` — unexpected; show `message`.

## What happens on the backend when approved (so the UI can reflect it)
- Source student: invoice `amountPaid` decreases by `amount`, `balance` increases, status may drop back to pending (`P`); their `Finance` (keyed by student + term + year) `paidAmount` decreases and `balance` increases.
- Destination student: invoice `amountPaid` increases by `amount`, `balance` decreases, status may become cleared (`C`); their `Finance` `paidAmount` increases and `balance` decreases.
- Both students' balances/statements should be refreshed after an approval.

## What to build
1. **API client**: add `createPaymentTransfer(payload)`, `approvePaymentTransfer(id, comment?)`, `rejectPaymentTransfer(id, reason)`, `listPaymentTransfers(status?)`, `getPaymentTransfer(id)`.
2. **Initiate form** (visible only with `payment_transfer:create`):
   - Field 1: **payment reference** (M-Pesa receipt code). On blur/lookup, fetch/derive and display the source student, source invoice, original amount, term/year, and payment method (read-only) so the maker confirms the right payment. (You can preview by creating on submit and reading the echoed fields, or add a lookup call if one is exposed.)
   - Field 2: **amount to transfer** — defaulting to the full transferable amount, with a "Transfer full amount" toggle and a max = transferable. Allow a smaller value for a partial transfer.
   - Field 3–4: **destination student** and **destination invoice** (invoice list filtered to the chosen student, same term/year).
   - Field 5: **reason** (optional).
   - Validate client-side: amount > 0 and ≤ transferable; destination ≠ source.
   - On `201`: confirm the request is pending approval and show the derived summary; do not show any balance change yet (`appliedAt` is null).
3. **Approval queue** (visible only with `payment_transfer:approve`):
   - List `PENDING_APPROVAL` transfers with source → destination, amount, term/year, maker, and reason.
   - Approve and Reject actions (Reject requires a reason). Show a confirmation dialog for Approve stating the money will move on both students.
   - **Disable/hide Approve and Reject on any transfer where `createdByName` is the current user** (maker ≠ checker). Show a hint like "You created this transfer; another approver must action it." The backend enforces this too, but disabling avoids a wasted round-trip.
   - On `403`/`409` from approve because of self-approval, surface the `message` and keep the row pending.
4. **Result / refresh**: after a successful approval, refresh both students' invoice lists, balances, and Finance/statement views. After a rejection, mark the transfer rejected and show its reason.
5. **History view**: filter transfers by `APPROVED` / `REJECTED` to audit past activity, showing maker, checker, timestamps, and (for rejected) the reason.

## Acceptance checks
- Entering a valid payment reference derives the source student, invoice, original amount, term/year, and method; the maker only picks amount + destination.
- Creating a transfer returns `201` with status `PENDING_APPROVAL`, `appliedAt: null`, and changes **no** balances until approved.
- A partial transfer (amount < original) is accepted; the `remainingTransferableAfter` reflects the leftover, and a second transfer can move the rest but not more than remains (over-amount returns `409`).
- A user who lacks `payment_transfer:approve` cannot see approve/reject controls.
- The maker of a transfer cannot approve it: the control is hidden/disabled, and a direct API attempt returns `409` and leaves the transfer pending.
- A different authorized checker can approve it: on `200`, the amount leaves the source student (balance up) and lands on the destination student (balance down), both statements reflect it, and `appliedAt` is set.
- Rejecting sets status `REJECTED` with the reason and moves no money.
- Approving or rejecting an already-actioned transfer returns `409`.
- If other transfers consumed the payment so the amount can no longer be moved, approval returns `409` with an explanatory `message`.
