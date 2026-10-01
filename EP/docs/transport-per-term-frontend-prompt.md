# Transport Fees Per Term — Front-End Prompt

Transport pricing and student assignments are now **per academic term** instead of a single flat
price per vehicle. A vehicle no longer has one `routePriceOneWay` / `routePriceTwoWay`; instead it
carries a list of **per-term prices** (one entry per `term` + `year`, each with a one-way and a
two-way amount). A student's one-way/two-way choice is now recorded **per term**, so the same
student can be two-way in Term 1 and one-way in Term 2.

This prompt updates the **existing** transport UI (vehicle create/edit, student assignment, and
transport payment screens). Base path is unchanged: `api/v1/transport/vehicle`.

> `transportType` is now an **enum**: the only accepted values are `"ONE_WAY"` and `"TWO_WAY"`.
> Stop sending free text like `"one way"` / `"oneway"` — send the exact enum string.
>
> `term` is an enum with values `"TERM_1"`, `"TERM_2"`, `"TERM_3"`. `year` is an integer (e.g. `2026`).

---

## 1. What changed on the vehicle

### Removed from request/response
- `routePriceOneWay`
- `routePriceTwoWay`

(The backend still keeps these columns for old data, but they are no longer part of the API and
must not be used for pricing.)

### Added: `termPrices`
The vehicle now has a `termPrices` array. Each entry:

```json
{
  "id": 12,            // present in responses only
  "term": "TERM_1",
  "year": 2026,
  "oneWayAmount": 3000,
  "twoWayAmount": 5000
}
```

### Create vehicle — `POST api/v1/transport/vehicle/create`

```json
{
  "vehicleNumber": "KDA 123X",
  "vehicleType": "Bus",
  "capacity": 40,
  "driverName": "John Doe",
  "driverContact": "0712345678",
  "route": "Westlands - CBD",
  "status": "active",
  "termPrices": [
    { "term": "TERM_1", "year": 2026, "oneWayAmount": 3000, "twoWayAmount": 5000 },
    { "term": "TERM_2", "year": 2026, "oneWayAmount": 3200, "twoWayAmount": 5200 },
    { "term": "TERM_3", "year": 2026, "oneWayAmount": 3200, "twoWayAmount": 5200 }
  ]
}
```

### Update vehicle — `PUT api/v1/transport/vehicle/update/{id}`
Same body shape. **Important:** when `termPrices` is present in the request, the backend
**replaces the entire set** of per-term prices with what you send (full replace, not merge). So:
- Load the vehicle, show its current `termPrices`, let the user edit/add/remove rows, then send
  back the **complete** list you want to persist.
- If you omit `termPrices` entirely (field absent), existing prices are left untouched. Sending an
  empty array `[]` clears all prices.

### Get vehicle — `GET api/v1/transport/vehicle/{id}` and `GET api/v1/transport/vehicle/all`
Responses now include `termPrices` (with `id` on each entry) and no longer include
`routePriceOneWay` / `routePriceTwoWay`.

### UI to build
On the vehicle create/edit form, replace the two flat price inputs with a small **per-term price
table**: a row per term/year with two amount inputs (one-way, two-way). Provide a way to add a row
(pick term + year) and remove a row. A common setup is three rows (Term 1/2/3) for the current
year.

---

## 2. What changed on student assignment

### `POST api/v1/transport/vehicle/assign`

Request now requires `term` and `year`, and `transportType` is the enum:

```json
{
  "studentId": 101,
  "vehicleId": 5,
  "pickupLocation": "Westlands Stage",
  "transportType": "TWO_WAY",
  "term": "TERM_1",
  "year": 2026
}
```

Behaviour and validation:
- A student may have **one assignment per (term, year)** — not one globally. Assigning the same
  student to another vehicle/type within the same term+year is rejected with a message like
  `Student already has a transport assignment for TERM_1 2026`.
- Assigning to a **different** term (or year) is allowed and expected — that's how per-term
  classification works.
- The backend rejects the assignment if the chosen vehicle has **no price configured for that
  term+year** (`No transport price configured for this vehicle in TERM_1 2026`). So set the
  vehicle's `termPrices` before assigning students to it for that term.
- Missing `term`, `year`, or `transportType` is rejected.

### UI to build
On the assign form, add a **Term** selector (Term 1/2/3) and a **Year** input (default to current
year), and change the transport-type control to emit `ONE_WAY` / `TWO_WAY`. Consider defaulting the
term to the current one. If you want a student's assignment for a new term, create a new assignment
for that term rather than editing the old one.

### Assignment / student-transport listings
- `GET api/v1/transport/vehicle/assignments`
- `GET api/v1/transport/vehicle/students-transport`

Both responses now include `term` and `year`, and `transportType` is the enum value. Show the term
column in these tables so users can see which term each assignment belongs to. Expect multiple rows
per student (one per term).

---

## 3. What changed on transport payments

### `POST api/v1/transport/vehicle/create-transport-transaction/{studentId}`

`term` and `year` were already accepted; `transportType` is now the enum. The **expected fee is now
resolved from the vehicle's per-term price** for the given `term` + `year` + direction — not the old
flat price.

```json
{
  "amount": 1500,
  "paymentMethod": "MPESA",
  "term": "TERM_1",
  "year": 2026,
  "vehicleId": 5,
  "transportType": "TWO_WAY"
}
```

New/updated error cases to surface to the user:
- Missing `term` or `year`: `Term and year are required to process a transport payment`.
- No price configured for that term/type:
  `Transport fee not configured for TWO_WAY in TERM_1 2026`.
- The existing arrears rules are unchanged: overpayment beyond the remaining balance is rejected,
  already-fully-paid is rejected, non-positive amounts are rejected. The response still returns
  `expectedFee`, `totalPaidBefore`, `totalPaidAfter`, `arrearsAfter`, `paymentStatus`
  (`COMPLETED` / `PARTIAL`).

### Transactions list — `GET api/v1/transport/vehicle/transactions/all`
Each item's `transportType` is now the enum string; `term` and `year` are already present. No other
change.

### UI to build
Ensure the payment screen sends `term`, `year`, and the enum `transportType`. Ideally pre-fill these
from the student's assignment for the currently selected term so the fee resolves correctly.

---

## 4. Migration / existing-data notes

- **Existing vehicles** have no `termPrices` yet. Until an admin sets per-term prices, assignments
  and payments for that vehicle/term will be rejected (no configured price). Prompt admins to open
  each vehicle and enter term prices.
- **Existing assignments** created before this change have `term = null` and `year = null` and may
  have a legacy `transportType`. Treat null term/year in listings as "unassigned term" and let users
  re-assign them under a specific term. New assignments always carry term/year.
- Anywhere the old UI read or wrote `routePriceOneWay` / `routePriceTwoWay`, remove it and use
  `termPrices` instead.

---

## 5. Quick checklist

- [ ] Vehicle form: replace flat one-way/two-way inputs with a per-term price table (`termPrices`).
- [ ] Vehicle edit: send the full `termPrices` list (replace semantics).
- [ ] Vehicle list/detail: render `termPrices`; stop referencing the removed flat price fields.
- [ ] Assign form: add Term + Year, send `transportType` as `ONE_WAY` / `TWO_WAY`.
- [ ] Assignment / students-transport tables: show `term` / `year`; expect one row per term.
- [ ] Payment form: send `term`, `year`, enum `transportType`; handle the new error messages.
- [ ] Replace all free-text transport-type strings with the enum everywhere.

---

# Transport Arrears By Term — Front-End Prompt (Addendum)

Because transport is now billed per term, each term is its own independent balance — they are
**not** netted against each other. A student who skipped Term 1 keeps a Term 1 debt even after
Term 2 is fully paid. Two read-only endpoints expose these balances so staff can see who owes what,
for which term. **No payment-flow change is required** — clearing an old term is just a normal
transport payment posted with that term + year (see section 3 above).

This is additive UI: a per-student arrears panel and a school-wide "who owes transport this term"
list. Base path unchanged: `api/v1/transport/vehicle`.

---

## A. Single student — `GET api/v1/transport/vehicle/arrears/student/{studentId}`

Returns the student's transport balances across **all** terms.

```json
{
  "statusCode": 200,
  "message": "Transport arrears retrieved successfully",
  "entity": {
    "studentId": 101,
    "admissionNumber": "ADM123",
    "fullName": "Jane Doe",
    "lines": [
      {
        "term": "TERM_1", "year": 2026, "transportType": "TWO_WAY",
        "vehicleId": 5, "vehicleNumber": "KDA 123X", "route": "Westlands - CBD",
        "expectedFee": 5000, "totalPaid": 0, "outstanding": 5000, "status": "UNPAID"
      },
      {
        "term": "TERM_2", "year": 2026, "transportType": "ONE_WAY",
        "vehicleId": 5, "vehicleNumber": "KDA 123X", "route": "Westlands - CBD",
        "expectedFee": 3000, "totalPaid": 3000, "outstanding": 0, "status": "COMPLETED"
      }
    ],
    "totalExpected": 8000,
    "totalPaid": 3000,
    "totalOutstanding": 5000
  }
}
```

Field notes:
- Each `lines[]` entry is one independent balance for a `(term, year, vehicle, transportType)`.
- `status` is one of `UNPAID`, `PARTIAL`, `COMPLETED`.
- `totalOutstanding` is the sum of the lines — the student's overall transport debt.
- A student with no transport transactions yet returns an empty `lines` array and zero totals.

### UI to build
Add a **Transport Arrears** panel on the student's fee/finance profile:
- A table of the `lines` (columns: Term, Year, Type, Vehicle/Route, Expected, Paid, Outstanding,
  Status). Sort oldest term first so unpaid history is obvious.
- A header showing `totalOutstanding` prominently (red when > 0, green/"cleared" when 0).
- Status chips: `UNPAID` (red), `PARTIAL` (amber), `COMPLETED` (green).
- On each line with `outstanding > 0`, a **"Pay this term"** action that opens the existing
  transport payment form **pre-filled** with that line's `term`, `year`, `vehicleId`, and
  `transportType`, and the amount defaulted to `outstanding`. This is what lets staff clear an old
  term without guessing values (the payment engine rejects a payment for the wrong term/type).

---

## B. School-wide for a term — `GET api/v1/transport/vehicle/arrears?term=TERM_1&year=2026`

Query params: `term` (`TERM_1` / `TERM_2` / `TERM_3`) and `year` (integer). Returns **only students
who still owe** for that term (fully-paid students are omitted).

```json
{
  "statusCode": 200,
  "message": "Found 2 student(s) with transport arrears for TERM_1 2026",
  "entity": [
    {
      "studentId": 101,
      "admissionNumber": "ADM123",
      "fullName": "Jane Doe",
      "lines": [
        {
          "term": "TERM_1", "year": 2026, "transportType": "TWO_WAY",
          "vehicleId": 5, "vehicleNumber": "KDA 123X", "route": "Westlands - CBD",
          "expectedFee": 5000, "totalPaid": 2000, "outstanding": 3000, "status": "PARTIAL"
        }
      ],
      "totalExpected": 5000,
      "totalPaid": 2000,
      "totalOutstanding": 3000
    }
  ]
}
```

Notes:
- The list is scoped to the requested term/year, so each student's `lines` here only cover that
  term (usually one line, or more if the student changed vehicle/type within the term).
- The response is an **array** of the same summary object as endpoint A.
- Because it's built from transactions, a student who was assigned but never had a payment posted
  for the term will **not** appear. Treat this list as "owes based on billed amounts," not
  "everyone assigned." (If you need the assigned-but-never-billed set too, ask backend to extend it.)

### UI to build
Add a **Transport Arrears Report** screen:
- Term selector (Term 1/2/3) + Year input, defaulting to the current term/year. On change, refetch.
- A table of students (Adm No, Name, Vehicle/Route, Expected, Paid, **Outstanding**, Status),
  sortable by outstanding descending.
- A footer/summary row: student count and the summed outstanding across the list.
- Row action **"Record payment"** → open the transport payment form pre-filled from that student's
  line (`term`, `year`, `vehicleId`, `transportType`, amount = `outstanding`).
- Optional: export/print for a bursar follow-up list; a "Send reminder" hook if SMS is wired.

---

## C. Behaviour to keep in mind

- **Terms don't cross-subsidise.** Paying the current term never reduces a previous term's
  outstanding, and vice-versa. Always post a payment against the specific term being cleared.
- **Amounts are already rounded** to 2 decimals server-side; just format as currency.
- **Consistency:** these numbers come from the same per-(term, year, vehicle, type) balance the
  payment engine uses, so an arrears line's `outstanding` equals the remaining balance the payment
  form will enforce as the maximum. Defaulting the pay amount to `outstanding` will always be
  accepted (assuming the price is still configured for that term).

---

## D. Arrears checklist

- [ ] Student profile: add Transport Arrears panel (per-term lines + total outstanding).
- [ ] "Pay this term" action pre-fills the payment form from the line (term/year/vehicle/type/amount).
- [ ] New screen: Transport Arrears Report with term + year selector (default current).
- [ ] Report table sortable by outstanding; footer with count + total.
- [ ] "Record payment" row action on the report pre-fills the payment form.
- [ ] Status chips: UNPAID / PARTIAL / COMPLETED.
- [ ] Format amounts as currency; render `totalOutstanding` prominently.
