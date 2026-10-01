# Transport Fee Payments (Maker-Checker) + Centralised Approvals — Front-End Integration Prompt

Two things in one doc:

1. A new **maker-checker workflow for transport fee payments** — a maker submits a payment, a
   different checker approves (posting it) or rejects (nothing happens). No balance changes until
   approval.
2. A plan to **centralise all maker-checker approval workflows** behind one reusable front-end
   abstraction (a single "Approvals" area + a generic approval adapter), so transport payments,
   transaction reversals, and future flows share the same UI, permission handling, and maker≠checker
   rules instead of each being rebuilt from scratch.

All responses use the standard wrapper `{ "message": string, "statusCode": number, "entity": <data|null> }`.
Auth is sent as the app already does; the tenant is resolved server-side — never send it.

---

## Part 1 — Transport fee payment approval

### 1.1 Permissions

| Permission string | Role in the flow | Gates |
|-------------------|------------------|-------|
| `transport_transaction:create` | **Maker** | Submit a payment; view the list/detail |
| `transport_transaction:approve` | **Checker** | Approve / reject a payment; view the list/detail |

- Show the submit action only with `transport_transaction:create`.
- Show Approve/Reject only with `transport_transaction:approve`.
- The backend enforces **maker ≠ checker**: the user who submitted a payment cannot approve it
  (HTTP 409). Hide/disable Approve on rows where `createdByName` is the current user.

### 1.2 Endpoints

Base path: `api/v1/transport/transactions`.

| Method & path | Permission | Body | Purpose |
|---------------|-----------|------|---------|
| `POST /submit/{studentId}` | `transport_transaction:create` | submit body (below) | Maker submits a transport payment. Stores a PENDING request; **no balance changes.** |
| `PUT /approve/{id}` | `transport_transaction:approve` | none | Checker approves. **Only here** is the real transaction posted and the balance moved. |
| `PUT /reject/{id}` | `transport_transaction:approve` | `{ "reason": "..." }` (optional) | Checker rejects. No balance changes. |
| `GET /` | either | — | List requests. Optional `?status=PENDING_APPROVAL\|APPROVED\|REJECTED`. |
| `GET /{id}` | either | — | Get one request. |

`{studentId}` is the student paying. `{id}` is the id of the pending request itself.

Submit body:

```json
{
  "amount": 3400,
  "paymentMethod": "MPESA",
  "term": "TERM_3",
  "year": 2026,
  "vehicleId": 7,
  "transportType": "ONE_WAY",
  "reason": "Parent paid at the office"
}
```

- `transportType` ∈ `ONE_WAY` | `TWO_WAY`. `term` ∈ `TERM_1` | `TERM_2` | `TERM_3`.
- `reason` is optional (maker justification).

### 1.3 Request object (`entity`)

```json
{
  "id": 5,
  "studentId": 755,
  "studentName": "Alex Wekesa",
  "admissionNumber": "ADM13097",
  "vehicleId": 7,
  "vehicleNumber": "KDA 123A",
  "route": "Ruiru Route",
  "transportType": "ONE_WAY",
  "term": "TERM_3",
  "year": 2026,
  "amount": 3400,
  "paymentMethod": "MPESA",
  "expectedFee": 3400,
  "totalPaidBeforeSnapshot": 0,
  "arrearsBeforeSnapshot": 3400,
  "reason": "Parent paid at the office",
  "status": "PENDING_APPROVAL",
  "createdByName": "accountant1",
  "createdAt": "2026-09-29T09:15:00",
  "approvedByName": null,
  "approvedAt": null,
  "postedTransactionId": null,
  "rejectionReason": null
}
```

- `expectedFee` / `totalPaidBeforeSnapshot` / `arrearsBeforeSnapshot` are a **snapshot at submit
  time**, for display. The authoritative figures are recomputed at approval, so what actually posts
  reflects the live balance — surface the snapshot as "at time of request".
- `postedTransactionId` is `null` until APPROVED, then holds the id of the posted
  `transport_transactions` row.

### 1.4 Behaviour the UI should communicate

- **Two-step:** submitting only creates a PENDING record. Nothing is posted and no balance moves.
  Say so explicitly ("Submitted for approval. No balance changed.").
- **On approval:** the payment is posted, the student's transport balance/arrears update, and the
  request flips to APPROVED with `postedTransactionId` set.
- **Re-validation at approval:** the backend re-checks fee configuration, "already fully paid", and
  overpayment against the **current** balance (not the submit-time snapshot). If the balance moved
  since submission (e.g. another payment was approved first), approval can fail with 409 — show the
  returned `message` and refresh.
- **One open request** per student + vehicle + term + year + transportType at a time (409 on a
  duplicate submit).

### 1.5 Errors to surface (read `message`)

| Status | Meaning |
|--------|---------|
| 400 | Missing/invalid field; amount ≤ 0; fee not configured for that vehicle/term/type; already fully paid; overpayment (message includes the max allowed). |
| 404 | Student, vehicle, or request not found. |
| 409 | A request is already pending for this student/vehicle/term/year/type; maker trying to approve own request; on approve, the payment is no longer valid against the current balance. |
| 500 | Unexpected error — approval rolls back, so nothing is half-posted. |

### 1.6 Note on the legacy direct-posting endpoint

There is a pre-existing endpoint that posts a transport payment **immediately** (no approval):
`POST /api/v1/transport/vehicle/create-transport-transaction/{studentId}`. For the maker-checker
model to hold, route all payment recording through `POST /api/v1/transport/transactions/submit/{studentId}`
and stop calling the direct endpoint from the UI. (Backend may deprecate/lock it — confirm with the
team.)

---

## Part 2 — Centralise the approval workflows

The app now has several independent maker-checker flows that all share the same shape
(submit → pending → approve/reject, with a status enum, maker/checker names, timestamps, and a
reason). Rather than a bespoke screen per flow, build **one Approvals module** and register each flow
as an adapter.

### 2.1 Flows to centralise

| Flow | Create/submit permission | Approve permission | Base path | Status values |
|------|--------------------------|--------------------|-----------|---------------|
| **Transport payment** (this doc) | `transport_transaction:create` | `transport_transaction:approve` | `api/v1/transport/transactions` | `PENDING_APPROVAL` / `APPROVED` / `REJECTED` |
| **Transaction reversal** | `transaction_reversal:create` | `transaction_reversal:approve` | `api/v1/transaction-reversals` | `PENDING_APPROVAL` / `APPROVED` / `REJECTED` |
| **Payment transfer** | `payment_transfer:create` | `payment_transfer:approve` | (payment-transfer endpoints) | approved/rejected/reversed |
| **Manual finance transaction** | `transaction:create` | `transaction:approve` | (pending-transaction endpoints) | `PENDING_APPROVAL` / `APPROVED` / `REJECTED` |

Transport payment, transaction reversal, and pending finance transaction already share an identical
status enum and list/get + approve/reject verb shape, so they map cleanly onto one abstraction.
(Invoice reversal is a related but slightly different, single-step void flow — keep it out of the
generic queue for now unless the team converts it to two-step.)

### 2.2 Shared abstraction (what to build)

Define one generic approval contract and one shared UI shell:

```ts
type ApprovalStatus = "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

// Normalised row every adapter maps its API object into.
interface ApprovalItem {
  id: number;
  kind: ApprovalKind;              // "TRANSPORT_PAYMENT" | "TRANSACTION_REVERSAL" | ...
  title: string;                   // e.g. "Transport payment — Alex Wekesa"
  subtitle?: string;               // e.g. "KDA 123A · TERM_3 2026 · ONE_WAY"
  amount?: number;
  reason?: string;
  status: ApprovalStatus;
  createdByName?: string;
  createdAt?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectionReason?: string;
  raw: unknown;                    // original API object for the detail view
}

interface ApprovalAdapter {
  kind: ApprovalKind;
  label: string;                   // "Transport payments"
  createPermission: string;        // e.g. "transport_transaction:create"
  approvePermission: string;       // e.g. "transport_transaction:approve"
  list(status?: ApprovalStatus): Promise<ApprovalItem[]>;
  get(id: number): Promise<ApprovalItem>;
  approve(id: number): Promise<void>;
  reject(id: number, reason?: string): Promise<void>;
  // Optional richer confirmation copy per kind (e.g. transfer warnings).
  approveConfirmMessage?(item: ApprovalItem): string;
}
```

The transport adapter maps the object in §1.3 into `ApprovalItem`
(`title = "Transport payment — " + studentName`,
`subtitle = vehicleNumber + " · " + term + " " + year + " · " + transportType`,
`amount = amount`), and calls the §1.2 endpoints.

### 2.3 Shared UI shell

One **Approvals** screen driven by the registered adapters:

- A **kind filter** (tabs or a dropdown) listing only the adapters the current user can see (has the
  create or approve permission for). Hide the whole Approvals area if the user has neither for any
  adapter.
- A **status filter** defaulting to `PENDING_APPROVAL`.
- A unified table: Type badge, Title, Subtitle, Amount, Maker (`createdByName`), Created, Status.
- Per-row actions available to checkers (`approvePermission`):
  - **Approve** — confirmation dialog (use `approveConfirmMessage(item)` when the adapter provides
    one, e.g. transfer reversals warn that two students are affected). Then `adapter.approve(id)`.
  - **Reject** — dialog with optional reason. Then `adapter.reject(id, reason)`.
  - Hide/disable Approve when `item.createdByName === currentUser` (maker ≠ checker; backend also
    returns 409).
- A **detail drawer/page** rendering `item.raw` per kind (transport shows fee/arrears snapshot; a
  reversal shows the transaction snapshot + transfer badge, etc.).
- Shared status styling: pending = amber, approved = green, rejected = grey. Shared loading/disabled
  states on submit/approve/reject. Shared error handling: on non-2xx show `message`; handle 409
  specially.

### 2.4 Submit entry points stay in-context

Centralise the **queue/approve/reject**, but keep **submission** where the user already is:
- Transport payment: a "Record payment" action on the student's transport screen / arrears row that
  calls `POST /api/v1/transport/transactions/submit/{studentId}` (see §1.2), gated by
  `transport_transaction:create`.
- Reversal: the existing "Reverse" action on a transaction row.

After a successful submit, the item shows up in the central Approvals queue for a checker.

---

## Part 3 — Front-end prompt (paste into your front-end assistant)

> Build a centralised **Approvals** module and wire the new transport fee payment workflow into it.
>
> **Generic abstraction**
> - Define `ApprovalStatus` (`PENDING_APPROVAL | APPROVED | REJECTED`), an `ApprovalItem`
>   normalised row, and an `ApprovalAdapter` interface with `list/get/approve/reject`, a `kind`,
>   `label`, and `createPermission`/`approvePermission` strings (shapes as specified above).
> - Build one **Approvals** screen: a kind filter (only adapters the user has a permission for), a
>   status filter (default PENDING_APPROVAL), a unified table, per-row Approve/Reject for checkers,
>   and a detail view rendering the raw object per kind.
> - Enforce maker ≠ checker in the UI: hide/disable Approve when the row's `createdByName` is the
>   current user; also handle the backend 409 for it.
> - Shared statuses: pending=amber, approved=green, rejected=grey. Shared loading + error handling:
>   all responses are `{ message, statusCode, entity }`; on non-2xx show `message`, handle 409
>   specially. Tenant is server-side — never send it.
>
> **Transport payment adapter** (`kind: "TRANSPORT_PAYMENT"`, label "Transport payments")
> - Permissions: create `transport_transaction:create`, approve `transport_transaction:approve`.
> - `list(status)` → `GET /api/v1/transport/transactions?status=...`;
>   `get(id)` → `GET /api/v1/transport/transactions/{id}`;
>   `approve(id)` → `PUT /api/v1/transport/transactions/approve/{id}`;
>   `reject(id, reason)` → `PUT /api/v1/transport/transactions/reject/{id}` with
>   `{ "reason": <text> }` (optional).
> - Map the API object to `ApprovalItem`: title "Transport payment — {studentName}", subtitle
>   "{vehicleNumber} · {term} {year} · {transportType}", amount = amount, reason = reason.
> - Detail view shows amount, paymentMethod, expectedFee, totalPaidBeforeSnapshot,
>   arrearsBeforeSnapshot (label these "at time of request"), createdByName/createdAt, and once
>   resolved: approvedByName/approvedAt, postedTransactionId, rejectionReason.
>
> **Transport payment submit (maker, in-context)**
> - On the student's transport / arrears view, add a "Record payment" action for
>   `transport_transaction:create`. Dialog fields: amount, paymentMethod, term, year, vehicle,
>   transportType, optional reason. Submit:
>   `POST /api/v1/transport/transactions/submit/{studentId}` with the body in §1.2.
> - On 201 show "Submitted for approval. No balance changed." Do not optimistically update the
>   student's balance — it changes only on approval.
> - Handle 400 (fee not configured / already fully paid / overpayment — show the message, it
>   includes the max allowed) and 409 (a request is already pending for this student/vehicle/term).
> - Stop calling the legacy direct-posting endpoint
>   `POST /api/v1/transport/vehicle/create-transport-transaction/{studentId}` from the UI.
>
> **Register existing flows as adapters** (so they render in the same queue)
> - Transaction reversal: create `transaction_reversal:create`, approve
>   `transaction_reversal:approve`, base `api/v1/transaction-reversals` (create is
>   `POST /create/{transactionId}` with `{reason}`; approve `PUT /approve/{id}`; reject
>   `PUT /reject/{id}` with optional `{reason}`; list `GET /`; get `GET /{id}`). Its
>   `approveConfirmMessage` should warn, when the reversal is `transferOriginated`, that both the
>   destination and source students' balances change and the money returns to the source student.
> - (Optional, same shape) Payment transfers and pending manual finance transactions — register
>   later using their own create/approve permissions and endpoints.
>
> **UX**
> - One place to see everything awaiting approval, filterable by kind and status.
> - Clear two-step messaging everywhere: submitting never moves money; approval does.
> - After approve/reject, refresh the queue and any affected student balance/statement views.

---

## Part 4 — Notes / assumptions

- Transport payments are a self-contained ledger (`transport_transactions` with running
  paid/arrears); a pending request writes **nothing** to that ledger until approval, so arrears
  reports and balances are never affected by unapproved requests.
- Approval re-computes and re-validates against the live balance, so two makers can't both get the
  same balance posted twice — the second approval fails with 409.
- Maker/checker identity is captured server-side from the authenticated user; the response exposes
  display names (`createdByName` / `approvedByName`) only.
- New permissions (`transport_transaction:create`, `transport_transaction:approve`) are enum-driven
  on the backend; existing admin roles may need a permission re-sync to gain them (same as prior
  maker-checker permissions).
