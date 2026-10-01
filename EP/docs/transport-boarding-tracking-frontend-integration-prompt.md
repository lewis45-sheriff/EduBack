# Transport Boarding Tracking — Front-End Integration Prompt

Records when a student **boards** or **alights** a school transport vehicle for a given service
date and leg (morning/evening, pickup/dropoff). Supports manual capture (the mandatory baseline),
optional biometric / NFC card / QR identification, offline batch sync with idempotency, an
expected-vs-actual **trip manifest**, and per-student **boarding history**. Everything is
tenant-isolated on the backend — the UI never sends a tenant id.

Build the UI to talk to the endpoints below exactly as specified. Do not invent fields.

---

## 1. Permissions

| Permission string | Gates |
|-------------------|-------|
| `transport_boarding:mark` | Record boarding events (the scan / mark-attendance screen) |
| `transport_boarding:read` | View the trip manifest and student boarding history |
| `transport_biometric:enroll` | Enrol a student's biometric / card / QR token |

- Show the "Boarding" area only to users with at least one of these.
- Gate the **Mark / Scan** action behind `transport_boarding:mark`.
- Gate the **Manifest** and **History** views behind `transport_boarding:read`.
- Gate the **Enrol token** action behind `transport_biometric:enroll`.
- The backend enforces authorization on every endpoint; do not rely on the UI checks alone. A
  missing permission returns HTTP 403.

---

## 2. Response wrapper

All endpoints return the standard wrapper:

```json
{ "message": "string", "statusCode": 200, "entity": <data|null> }
```

Read `entity` for the payload and use `statusCode` (not just the HTTP status) for branching.
`message` is safe to surface to users. The backend never returns SQL, stack traces, tenant ids,
or raw token values, so it is safe to show `message` directly.

---

## 3. Enums (send canonical values)

**`leg`** — `MORNING_PICKUP`, `MORNING_DROPOFF`, `EVENING_PICKUP`, `EVENING_DROPOFF`

```
MORNING_PICKUP    Student boards at the morning stop, travels toward school
MORNING_DROPOFF   Student alights at school
EVENING_PICKUP    Student boards at school, travels toward home
EVENING_DROPOFF   Student alights at the home/drop-off stop
```

**`method`** — `MANUAL`, `BIOMETRIC`, `NFC_CARD`, `QR`

**`status`** (read-only, on recorded events) — `ON_TIME`, `LATE`, `MANUAL_OVERRIDE`
New events are always `ON_TIME`; the UI never sets status.

**`transportType`** (read-only context) — `ONE_WAY`, `TWO_WAY`

> Request bodies (`scan`, `enroll`) tolerate case/separator variants, but **query params**
> (`leg` in the manifest URL) must be the exact canonical uppercase value, e.g. `MORNING_PICKUP`.

---

## 4. Endpoints

| Method & path | Permission | Purpose |
|---------------|-----------|---------|
| `POST /api/v1/transport/boarding/scan` | `transport_boarding:mark` | Record one or more boarding events (batch, idempotent) |
| `GET /api/v1/transport/boarding?vehicleId=&date=&leg=` | `transport_boarding:read` | Trip manifest: expected vs actual for a vehicle/date/leg |
| `GET /api/v1/transport/boarding/student/{studentId}?from=&to=` | `transport_boarding:read` | Student boarding history grouped by day |
| `POST /api/v1/transport/biometric/enroll` | `transport_biometric:enroll` | Enrol a biometric/card/QR token for a student |

`date`, `from`, `to` are ISO dates: `YYYY-MM-DD`.

---

## 5. Record boarding events — `POST /api/v1/transport/boarding/scan`

### Request

```json
{
  "events": [
    {
      "clientEventId": "8e9d4f30-1f8a-4e43-9e2d-8a2d7b7f0b21",
      "studentId": 101,
      "admissionNumber": null,
      "biometricId": null,
      "vehicleId": 5,
      "leg": "MORNING_PICKUP",
      "method": "MANUAL",
      "capturedAt": "2026-09-29T06:42:11+03:00",
      "deviceId": "CONDUCTOR-PHONE-1",
      "latitude": -1.2921,
      "longitude": 36.8219
    }
  ]
}
```

### Per-event field rules (enforce in the client to avoid round-trips)

- **`clientEventId`** — optional but **strongly recommended**. Generate a UUID **on the client per
  event** and reuse it on retries. This is the idempotency key for offline sync.
- **Exactly one** student identifier: `studentId`, **or** `admissionNumber`, **or** `biometricId`.
  Sending zero or more than one is rejected.
  - The neutral field name `identifierToken` is also accepted as an alias of `biometricId`; prefer
    `biometricId` to match this contract, or `identifierToken` if you want to avoid implying the
    token is biometric for NFC/QR.
- **Identifier must match `method`:**
  - `MANUAL` → `studentId` or `admissionNumber` (no token)
  - `BIOMETRIC` / `NFC_CARD` / `QR` → token in `biometricId` (no `studentId`/`admissionNumber`)
- **`vehicleId`**, **`leg`**, **`method`**, **`capturedAt`** are required.
- **`capturedAt`** — send an **offset-aware** ISO timestamp (e.g. `...+03:00`). The backend converts
  it to the school timezone (Africa/Nairobi) and derives the service date from it. **Do not send a
  separate service date** — it is ignored/never trusted.
- **`latitude`** −90…90, **`longitude`** −180…180 when supplied (both optional).
- **`deviceId`** optional.

### Response (HTTP 200; `statusCode` 200)

```json
{
  "message": "Processed 3 event(s): 1 accepted, 1 duplicate, 1 rejected",
  "statusCode": 200,
  "entity": {
    "accepted": [
      { "clientEventId": "8e9d...", "eventId": 900, "studentId": 101, "status": "RECORDED", "reason": null }
    ],
    "duplicates": [
      { "clientEventId": "a1b2c3", "eventId": 901, "studentId": 102, "status": "DUPLICATE", "reason": null }
    ],
    "rejected": [
      { "clientEventId": "x1y2z3", "eventId": null, "studentId": null, "status": "REJECTED",
        "reason": "Student is not assigned to the specified vehicle for the service date." }
    ],
    "acceptedCount": 1,
    "duplicateCount": 1,
    "rejectedCount": 1
  }
}
```

### How to handle the response

- The batch is **not all-or-nothing**. Reconcile **per event** using the three buckets, matching on
  `clientEventId` (falling back to array order when a `clientEventId` was not supplied).
- **`accepted`** → mark that scan as recorded; store `eventId`.
- **`duplicates`** → treat as **success/no-op** (the attendance already exists). Show a subtle
  "already recorded" state, not an error. `eventId` is usually present; in a rare concurrent race it
  can be `null` while `studentId` is still returned — still treat as recorded.
- **`rejected`** → surface `reason` next to that scan and let the operator fix and retry. Common
  reasons:
  - `Student is not assigned to the specified vehicle for the service date.`
  - `Student is not expected on <LEG> for the current transport assignment.` (e.g. a `ONE_WAY`
    morning student scanned on `EVENING_PICKUP`)
  - `Student not found.` / `Vehicle not found.`
  - `Provide exactly one student identifier ...` / `Method BIOMETRIC requires an identification token.`
  - `No academic term is configured for the service date <date>.`
- A missing/empty `events` array returns `statusCode` 400.

### Idempotency & offline sync (important)

- The endpoint is **safe to call repeatedly** with the same batch. Re-submitting recorded events
  returns them as `duplicates`, never creating a second attendance row.
- Business duplicate = same **student + service date + leg** (regardless of vehicle/method/device/
  time). The **first valid write wins**; a repeat scan is a no-op and does **not** overwrite the
  original.
- Recommended offline pattern:
  1. Queue events locally, each with a client-generated `clientEventId`.
  2. On connectivity, POST the queued batch.
  3. Remove `accepted` **and** `duplicates` from the queue (both are terminal-success).
  4. Keep `rejected` for operator review; auto-retry only transport/network errors (5xx / no
     response), not `rejected` outcomes.

---

## 6. Trip manifest — `GET /api/v1/transport/boarding?vehicleId=&date=&leg=`

All three query params are **required**. `date` = `YYYY-MM-DD`, `leg` = canonical enum.

```
GET /api/v1/transport/boarding?vehicleId=5&date=2026-09-29&leg=MORNING_PICKUP
```

### Response `entity`

```json
{
  "vehicleId": 5,
  "vehicleNumber": "KDA 123A",
  "route": "Ruiru Route",
  "date": "2026-09-29",
  "leg": "MORNING_PICKUP",
  "expectedCount": 30,
  "boardedCount": 28,
  "missingCount": 2,
  "boarded": [
    {
      "studentId": 101,
      "admissionNumber": "ADM101",
      "fullName": "Student Name",
      "transportType": "TWO_WAY",
      "method": "MANUAL",
      "capturedAt": "2026-09-29T06:42:11",
      "status": "ON_TIME"
    }
  ],
  "missing": [
    { "studentId": 102, "admissionNumber": "ADM102", "fullName": "Student Name", "transportType": "TWO_WAY" }
  ]
}
```

- **Expected** = students assigned to that vehicle for the term/year of `date` **and** expected on
  that specific `leg` (a `ONE_WAY` morning student appears on morning legs only). It is not simply
  every student on the vehicle.
- `missing = expected − boarded` by `studentId`.
- Build the UI as three counters (expected / boarded / missing) plus a table with a Boarded/Missing
  toggle. `capturedAt` here is already in school-local time (no offset).
- 404 when the vehicle does not exist; 400 when a param is missing or no term covers `date`.

---

## 7. Student boarding history — `GET /api/v1/transport/boarding/student/{studentId}?from=&to=`

`from` and `to` are required ISO dates; `from <= to`; range capped at **366 days** (else 400).

### Response `entity`

```json
{
  "studentId": 101,
  "transportType": "TWO_WAY",
  "from": "2026-09-01",
  "to": "2026-09-29",
  "days": [
    {
      "serviceDate": "2026-09-29",
      "events": [
        { "leg": "MORNING_PICKUP",  "capturedAt": "2026-09-29T06:42:11", "method": "MANUAL",    "status": "ON_TIME" },
        { "leg": "MORNING_DROPOFF", "capturedAt": "2026-09-29T07:31:20", "method": "BIOMETRIC", "status": "ON_TIME" }
      ]
    }
  ]
}
```

- Only **actual recorded** events are returned — missing legs are **not** fabricated. Days are
  ordered ascending; events within a day are ordered by leg.
- If you want to show "expected but not recorded" vs "not applicable", use the top-level
  `transportType` to know which legs apply (`TWO_WAY` = all four, `ONE_WAY` = morning pickup +
  dropoff) and diff against the events present that day. Do not assume a missing leg means absence
  without that context.
- 404 when the student does not exist.

---

## 8. Enrol a token — `POST /api/v1/transport/biometric/enroll`

Optional feature. Manual attendance works with no enrollment at all, so this screen is not a
prerequisite for boarding.

### Request

```json
{
  "studentId": 101,
  "biometricId": "AF93...",
  "method": "BIOMETRIC",
  "deviceVendor": "ExampleVendor"
}
```

- `studentId` and `method` required; `method` must be `BIOMETRIC`, `NFC_CARD`, or `QR` —
  **`MANUAL` is rejected** (400).
- Token goes in `biometricId` (or `identifierToken`).
- **Never** send raw fingerprints, images, or raw templates — only the opaque token from the
  approved device/SDK.

### Response `entity` (HTTP 201)

```json
{
  "id": 50,
  "studentId": 101,
  "biometricId": "AF93****92",
  "method": "BIOMETRIC",
  "enrolledAt": "2026-09-29T06:30:00"
}
```

- The returned `biometricId` is **masked** (`AF93****92`). Do not attempt to recover or display the
  full token; the backend never returns it.
- Upsert behaviour: re-enrolling the **same token for the same student** refreshes the mapping;
  enrolling a **new token** for a method the student already has replaces that method's token.
- Enrolling a token that already belongs to a **different student** returns **409 Conflict** — show
  "This token is already assigned to another student." Do not offer a "force transfer" option.

---

## 9. UI checklist

- [ ] Boarding module visible only with a relevant permission; actions gated per §1.
- [ ] Scan/mark screen: pick vehicle + leg, then mark students (manual list, or scan via device
      for biometric/NFC/QR). Enforce exactly-one-identifier and identifier↔method rules client-side.
- [ ] Generate a `clientEventId` (UUID) per event and reuse on retry.
- [ ] Send offset-aware `capturedAt`; never send a service date.
- [ ] Reconcile the scan response by bucket per `clientEventId`; treat `duplicates` as success;
      show `reason` for `rejected`.
- [ ] Offline queue that re-submits safely and clears accepted + duplicates.
- [ ] Manifest view with expected/boarded/missing counters and a Boarded/Missing table.
- [ ] Student history grouped by day; use `transportType` to interpret missing legs.
- [ ] Enrollment screen (optional) that never handles raw biometric data and shows the masked token;
      handle the 409 different-student case.
- [ ] Branch on `entity.statusCode`; surface `message`; treat 403 as "no permission".
```
