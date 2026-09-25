# Transaction Reversal (Maker-Checker) — Front-End Integration Prompt

Lets an authorised user request the reversal (deletion) of a finance transaction, and a
second user approve or reject it. On approval the linked **invoice** and **finance balances**
are restored to their pre-transaction state; if the transaction came from a student-to-student
**payment transfer**, the money is returned to the **original (source) student**. No balances
change until a different user approves — this is a maker-checker flow.

---

## 1. Permissions

| Permission string | Role in the flow | Gates |
|-------------------|------------------|-------|
| `transaction_reversal:create` | **Maker** | Request a reversal; view the list/detail |
| `transaction_reversal:approve` | **Checker** | Approve / reject a reversal; view the list/detail |

- Show the "Reversals" area / actions only to users with at least one of these.
- The **Approve** and **Reject** actions require `transaction_reversal:approve`.
- The **Request reversal** action requires `transaction_reversal:create`.
- The backend enforces **maker ≠ checker**: the same user who created a reversal cannot
  approve it (returns HTTP 409). Reflect this in the UI by hiding/disabling Approve on
  requests the current user created.

---

## 2. Endpoints

Base path: `api/v1/transaction-reversals`. All responses use the standard wrapper
`{ "message": string, "statusCode": number, "entity": <data|null> }`.

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `POST /create/{transactionId}` | `transaction_reversal:create` | `{ "reason": "..." }` (required) | Maker requests reversal of finance transaction `transactionId`. Stores a PENDING request; **no balances change.** |
| `PUT /approve/{id}` | `transaction_reversal:approve` | none | Checker approves reversal `id`. Restores invoice + finance; returns transfer money to the source student when applicable. |
| `PUT /reject/{id}` | `transaction_reversal:approve` | `{ "reason": "..." }` (optional) | Checker rejects reversal `id`. No balances change. |
| `GET /` | either | — | List reversals. Optional `?status=PENDING_APPROVAL\|APPROVED\|REJECTED`. |
| `GET /{id}` | either | — | Get one reversal. |

`{transactionId}` is the id of the `finance_transactions` row being reversed (e.g. from the
fee statement / transactions list). `{id}` is the id of the reversal request itself.

### Reversal object (`entity`)

```json
{
  "id": 10,
  "financeTransactionId": 16,
  "studentId": 724,
  "studentName": "Geoffrey Atieno",
  "invoiceId": 88,
  "transactionType": "INCOME",          // or EXPENSE
  "category": "School Fees",            // or PAYMENT_TRANSFER for transfer legs
  "amount": 2800.00,
  "transactionReference": "TXN1790253454469",
  "term": "TERM_3",
  "academicYear": 2026,
  "transferOriginated": false,          // true when it came from a payment transfer
  "paymentTransferId": null,            // the transfer id when transferOriginated
  "reason": "Duplicate entry",
  "status": "PENDING_APPROVAL",         // PENDING_APPROVAL | APPROVED | REJECTED
  "createdByName": "accountant1",
  "createdAt": "2026-09-25T14:00:00",
  "approvedByName": null,
  "approvedAt": null,
  "appliedAt": null,
  "rejectionReason": null
}
```

---

## 3. Behaviour the UI should communicate

- **Two-step flow:** requesting a reversal only creates a PENDING record — nothing is
  deleted or refunded yet. Make this explicit ("Sent for approval. No balances changed.").
- **On approval:** the target transaction is removed and the invoice returns to its earlier
  state (amountPaid/balance/status recomputed), and the student's finance balance is
  restored.
- **Transfer-originated transactions** (`transferOriginated: true`, or reference starting
  `TRF-IN-`/`TRF-OUT-`, or `category = "PAYMENT_TRANSFER"`): approving the reversal undoes
  **both** legs — the money is taken back from the destination student and returned to the
  original source student, and the underlying transfer is marked `REVERSED`. Surface this in
  the confirmation dialog so the checker understands both students are affected.
- **One reversal per transaction:** a transaction that already has a pending or approved
  reversal cannot be requested again (backend returns HTTP 409).

---

## 4. Error responses to surface (read `message`)

| Status | Meaning |
|--------|---------|
| 400 | Missing `reason`, or `financeTransactionId` missing. |
| 404 | Transaction / reversal not found. |
| 409 | Already pending/applied; maker trying to approve own request; transfer not in an APPROVED state; transaction not linked to an invoice. |
| 500 | Unexpected error (e.g. finance/invoice record missing) — the whole approval rolls back, so nothing is half-applied. |

---

## 5. Front-end prompt (paste into your front-end assistant)

> Add a **Transaction Reversal** capability with a maker-checker workflow.
>
> **Permissions**
> - `transaction_reversal:create` — can request a reversal and view reversals.
> - `transaction_reversal:approve` — can approve/reject reversals and view them.
> - Show the "Request reversal" action only with `transaction_reversal:create`; show
>   Approve/Reject only with `transaction_reversal:approve`.
> - The backend rejects a user approving their own request (HTTP 409). Also hide/disable the
>   Approve button on rows where `createdByName` is the current user.
>
> **Requesting a reversal (maker)**
> - From a transaction row (e.g. in a student's transactions list or the fee statement),
>   add a "Reverse" action for users with `transaction_reversal:create`.
> - Open a dialog with a required "Reason" textarea. On submit:
>   `POST /api/v1/transaction-reversals/create/{transactionId}` with body
>   `{ "reason": "<text>" }`.
> - On success (201) show "Reversal requested and pending approval. No balances changed."
>   Do not optimistically remove the transaction — it is not reversed until approved.
>
> **Reversals queue (checker)**
> - Add a "Reversals" screen listing `GET /api/v1/transaction-reversals` with a status
>   filter (PENDING_APPROVAL / APPROVED / REJECTED). Default to PENDING_APPROVAL.
> - Each row shows student, amount, transaction type/category, reason, maker (createdByName),
>   created date, status, and — when `transferOriginated` is true — a clear "Transfer" badge.
> - For a PENDING row, a checker sees **Approve** and **Reject**:
>   - Approve: `PUT /api/v1/transaction-reversals/approve/{id}`. If the row is
>     `transferOriginated`, the confirmation dialog must warn that BOTH the destination and
>     the original source student's balances will be adjusted and the money returned to the
>     source student.
>   - Reject: `PUT /api/v1/transaction-reversals/reject/{id}` with optional
>     `{ "reason": "<text>" }`.
> - After approve/reject, refresh the list and any affected student's balance/invoice views.
>
> **API + errors**
> - All responses are `{ message, statusCode, entity }`. On non-2xx, show `message` as an
>   error toast. Handle 409 specially (already pending, maker≠checker, transfer state,
>   no linked invoice) with the returned message.
> - Send auth as the app already does; tenant is resolved server-side (never send it).
>
> **UX**
> - Loading states on submit/approve/reject.
> - Distinguish the three statuses visually (pending = amber, approved = green,
>   rejected = grey).
> - Show `approvedByName` / `approvedAt` / `rejectionReason` on resolved rows for audit
>   visibility.

---

## 6. Deletion eligibility by origin (important)

Not every transaction can be deleted. The backend now records an origin (`source`) on each
finance transaction and enforces:

| Origin | Deletable? | Behaviour on approval |
|--------|-----------|-----------------------|
| Manual entry (`MANUAL`) | ✅ Yes | Transaction deleted; invoice + finance restored. |
| M-Pesa callback / paybill (`MPESA_CALLBACK`) | ❌ No | Request is refused (HTTP 409). Money can only be **transferred**. |
| M-Pesa STK push (`MPESA_STK`) | ❌ No | Request refused (HTTP 409). |
| Payment-transfer leg (`PAYMENT_TRANSFER`) | ✅ Yes | Both legs undone; money returned to the original **source** student; transfer marked `REVERSED`. |
| Other/system | ❌ No | Request refused. |

Front-end guidance:
- Only show a "Delete" / "Request reversal" action on transactions that are deletable. If you
  do not have the origin on the client, still handle the HTTP 409 with the message
  *"…originated from an M-Pesa payment and cannot be deleted. It can only be transferred to
  another student."* and, for M-Pesa payments, surface the **Transfer** action instead.
- For M-Pesa payments, point the user at the payment-transfer flow rather than deletion.

## 7. Notes / assumptions (backend defaults chosen)

- On approval the target `FinanceTransaction` is **hard-deleted**; the `TransactionReversal`
  row is the durable audit record (who requested, who approved, when, reason).
- M-Pesa (callback / STK) payments are **blocked from deletion** at request time. Legacy rows
  with no recorded origin are treated as non-deletable when their payment method is M-Pesa,
  so historical callback payments stay protected.
- Transfer legs resolve the `PaymentTransfer` by the id embedded in the reference; the
  transfer must be `APPROVED` to be reversed, after which it becomes `REVERSED`.
- Only transactions linked to an invoice (`invoiceId` present) are eligible.
