# Transport Boarding Tracking & Attendance — Front-End Prompt

Students who use school transport are marked **at the bus** as they board and alight. Each
mark is a **boarding event**. Combined with a student's per-term one-way/two-way assignment, this
gives transport attendance ("did the child board?") and drives parent alerts ("your child boarded"
/ "your child did not board this morning"). It must work **offline** because buses lose signal.

> **Biometrics is optional.** The whole feature works with **manual** capture alone — a conductor
> taps students off a list. Biometric fingerprint/face and card/QR scanning are an **add-on** a
> school can enable (or never enable). Build the manual path as the always-available baseline, and
> treat biometric/card/QR as an enhancement layered on top of the exact same boarding-event model.
> Nothing in tracking, attendance, or notifications depends on biometrics being present.

> Status: this documents the **planned API contract** for the bus capture app and the office
> tracking screens. Build the UI against these shapes; the backend endpoints are being added in
> parallel. Anything marked *(TBD)* needs a backend confirm before going live.

Base path (planned): `api/v1/transport`.

---

## 1. Concepts

### Trip legs
A school day has up to four legs. Each scan records exactly one:

| leg | meaning | who does it |
| --- | --- | --- |
| `MORNING_PICKUP` | boards at the stop, heading to school | all transport students |
| `MORNING_DROPOFF` | alights at school | all transport students |
| `EVENING_PICKUP` | boards at school, heading home | two-way students |
| `EVENING_DROPOFF` | alights at the stop | two-way students |

### One-way vs two-way (ties into existing per-term assignment)
Each student has a per-term assignment with `transportType` = `ONE_WAY` or `TWO_WAY`
(see `transport-per-term-frontend-prompt.md`).
- `ONE_WAY` → expected legs: `MORNING_PICKUP`, `MORNING_DROPOFF` *(assumes one-way = morning to
  school; confirm with backend — see Open Questions)*.
- `TWO_WAY` → expected legs: all four.

**Expected vs actual** is the whole point: for a vehicle + date + leg, the *expected* students are
those assigned to that vehicle this term whose type includes that leg; the *actual* students are
those with a boarding event. `expected − actual = missing`, which is the attendance list and the
trigger for "did not board" alerts.

### Capture methods
`method` on each event is one of `MANUAL`, `BIOMETRIC`, `NFC_CARD`, `QR`.
- **`MANUAL`** is the baseline and always available — a conductor taps the student off the expected
  list. A school can run the entire feature on manual only.
- **`BIOMETRIC` / `NFC_CARD` / `QR`** are the optional add-on. They only apply when the school has
  enabled biometric/card capture and enrolled students (section 2).

Tag how each event was captured (manual entries are visually distinct and auditable). The boarding
event, tracking, attendance, and alerts are **identical** regardless of method — only the
identifier and `method` differ.

---

## 2. Biometrics / card / QR — the optional layer

This entire section applies **only if the school opts in**. If biometrics is off, skip the
enrollment screen and the biometric scan path entirely; the manual flow (section 4A) covers
everything.

**Feature flag.** Assume a per-school setting `transportBiometricsEnabled` *(TBD — confirm the
config source with backend)*. When `false`: hide enrollment, hide the "scan" mode on the bus app,
and use manual capture only. When `true`: additionally offer enrollment and scan capture.

**Raw biometric data never leaves the device and is never sent to this API.** The device SDK stores
the fingerprint/face template locally and produces an opaque **`biometricId`** token. The flow:

1. **Enrollment** (office/admin app): capture the student's biometric on the device → the SDK
   returns a `biometricId` → POST it to the enroll endpoint to map it to the student. You send only
   the token, never the biometric image/template.
2. **On the bus:** the scanner matches locally, resolves to a `biometricId`, and the capture app
   sends a boarding event referencing that token.
3. Any device that yields a stable token works (fingerprint, RFID card, face, QR) — the front end
   treats it as an opaque string. Not every student needs to be enrolled: an un-enrolled student is
   still fully markable via the manual list, so schools can enroll gradually or partially.

So when the add-on is on, the front end has two extra responsibilities: an **enrollment screen** and
a **scan mode** in the capture app. It never handles raw biometric bytes.

---

## 3. Endpoints (planned contract)

### 3.1 Enroll a student's biometric/card — `POST api/v1/transport/biometric/enroll` *(optional add-on only)*
Only used when the biometric/card add-on is enabled. The manual flow needs no enrollment.
```json
{ "studentId": 101, "biometricId": "AF93...opaque", "method": "BIOMETRIC", "deviceVendor": "ZKTeco" }
```
Response: `{ statusCode, message, entity: { id, studentId, biometricId, enrolledAt } }`.
Re-enrolling the same student replaces the mapping. `method` may also be `NFC_CARD` / `QR` when the
token is a card UID / QR payload.

### 3.2 Record boarding events — `POST api/v1/transport/boarding/scan`
The **core endpoint**, used by both manual and (optional) biometric capture. Accepts **one event or
a batch** (send an array for offline sync). Idempotent.

```json
{
  "events": [
    {
      "eventId": "c1f2-...client-generated-uuid",
      "studentId": 101,                 // manual: use studentId or admissionNumber
      "vehicleId": 5,
      "leg": "MORNING_PICKUP",
      "method": "MANUAL",
      "capturedAt": "2026-09-29T06:42:11+03:00",
      "deviceId": "CONDUCTOR-PHONE-1",
      "location": { "lat": -1.2921, "lng": 36.8219 }   // optional
    },
    {
      "eventId": "d5a7-...another-uuid",
      "biometricId": "AF93...",         // optional add-on: token instead of studentId
      "vehicleId": 5,
      "leg": "MORNING_PICKUP",
      "method": "BIOMETRIC",
      "capturedAt": "2026-09-29T06:43:02+03:00",
      "deviceId": "BUS-5-SCANNER-1"
    }
  ]
}
```
Rules the UI must honour:
- Provide **exactly one** identifier per event: `studentId`, `admissionNumber`, or `biometricId`.
  Manual capture uses `studentId`/`admissionNumber`; the optional scanner uses `biometricId`.
- `eventId` is a **client-generated** UUID. Reusing it makes the call idempotent — safe to resend a
  queued batch after a flaky connection without creating duplicates.
- `capturedAt` is the real mark time on the device (ISO-8601 with offset), **not** the upload time.
  This matters for offline batches uploaded hours later.

Response reports per-event outcome so the app can clear only what succeeded:
```json
{
  "statusCode": 200,
  "message": "Processed 3 event(s)",
  "entity": {
    "accepted": [ { "eventId": "c1f2-...", "studentId": 101, "status": "RECORDED" } ],
    "duplicates": [ { "eventId": "a77b-...", "status": "DUPLICATE" } ],
    "rejected": [ { "eventId": "9x0-...", "reason": "Unknown identifier (student/biometricId not found)" } ]
  }
}
```

### 3.3 Trip manifest / attendance — `GET api/v1/transport/boarding?vehicleId=5&date=2026-09-29&leg=MORNING_PICKUP`
Returns expected vs actual for that leg.
```json
{
  "statusCode": 200,
  "message": "...",
  "entity": {
    "vehicleId": 5, "vehicleNumber": "KDA 123X", "route": "Westlands - CBD",
    "date": "2026-09-29", "leg": "MORNING_PICKUP",
    "expectedCount": 20, "boardedCount": 18, "missingCount": 2,
    "boarded": [
      { "studentId": 101, "admissionNumber": "ADM123", "fullName": "Jane Doe",
        "transportType": "TWO_WAY", "method": "BIOMETRIC", "capturedAt": "2026-09-29T06:42:11+03:00",
        "status": "ON_TIME" }
    ],
    "missing": [
      { "studentId": 205, "admissionNumber": "ADM777", "fullName": "Sam Kip", "transportType": "ONE_WAY" }
    ]
  }
}
```

### 3.4 Student boarding history — `GET api/v1/transport/boarding/student/{studentId}?from=2026-09-01&to=2026-09-29`
Returns the student's events in the range, grouped by date with the legs present/absent per day.

---

## 4. UI to build

### A. Bus Capture App (driver/conductor, mobile, offline-first)
This is the critical piece. Design for a phone/tablet on a moving bus with no signal.
- **Pick trip context first:** vehicle (usually fixed to the device), leg (Morning Pickup / Morning
  Dropoff / Evening Pickup / Evening Dropoff), date defaults to today. Persist this so marking is
  one-tap thereafter.
- **Manual capture (baseline, always present):** a searchable list of the students *expected* on
  this vehicle/leg; tapping a name records a `MANUAL` event. Show who's already boarded (checked) vs
  not, and a "mark all remaining absent" is **not** needed — missing is derived automatically. This
  path alone is a complete, shippable feature.
- **Scan mode (optional add-on, only when biometrics/card enabled):** big feedback on each scan —
  student photo + name + a green check, or a red "not recognised." Play a distinct sound for success
  vs failure (driver isn't looking at the screen). If a scan fails or the student isn't enrolled,
  fall back to the manual list — never a dead end. Hide this mode entirely when the add-on is off.
- **Offline queue:** every mark (manual or scan) is written to a local queue with a generated
  `eventId` and the real `capturedAt`. Show a pending-sync badge (e.g. "12 unsent"). Auto-flush to
  `/boarding/scan` when online; on response, remove `accepted` + `duplicates` from the queue,
  surface `rejected` for review. Never block marking on network.
- **Live manifest:** show boarded / expected / missing counts for the current leg so the driver
  knows when everyone expected is on board before leaving.

### B. Enrollment screen (office/admin) — *(optional add-on only; omit if biometrics is off)*
- Search a student → "Enrol biometric/card" → trigger the device SDK → on success POST the returned
  `biometricId` to `/biometric/enroll`. Show enrolled status and allow re-enrol.
- Support enrolling a card (`NFC_CARD`) or QR the same way — same endpoint, different `method`.
- Enrollment is not required for a student to be tracked — un-enrolled students are marked manually.
  Show enrollment as an optional convenience, not a prerequisite.

### C. Office tracking / attendance screens
- **Trip attendance:** pick vehicle + date + leg → render the manifest from 3.3 (boarded list with
  time + method, and a highlighted **missing** list). This is the daily "who didn't board" view.
- **Student transport history:** on the student profile, a timeline from 3.4 showing each day's legs
  (e.g. ✓ Morning Pickup 06:42, ✓ Morning Dropoff 07:15, ✗ Evening — for a one-way student the
  evening legs simply aren't expected, render them as "n/a" not "missing").
- **Parent-facing (optional):** the same history filtered to the parent's child(ren), plus push/SMS
  when a scan is recorded or a leg is missed after a cutoff.

---

## 5. Parent notifications (ties into existing SMS)
*(TBD trigger policy — confirm with backend.)* Two candidate triggers:
- **On scan:** "Jane boarded KDA 123X at 06:42." Reassuring but high volume.
- **On missed leg:** after a per-leg cutoff time, if an expected student has no event, alert the
  parent: "Jane was not on the morning bus." Lower volume, higher signal.
Recommend starting with **missed-leg alerts** only, with on-scan as an opt-in per parent. Reuse the
existing SMS integration; the front end just exposes the toggle in parent/student settings.

---

## 6. Open questions for backend (resolve before go-live)
1. **Biometrics feature flag:** where is `transportBiometricsEnabled` configured (per-school
   setting / tenant config), and how does the front end read it to show/hide the enrollment + scan
   surfaces? (Manual works regardless.)
2. **One-way direction:** is `ONE_WAY` always morning-to-school, or can it be evening-only? This
   decides which legs are "expected" for one-way students.
3. **Notification policy:** on every mark, on missed-leg only, or both (per-parent opt-in)?
4. **Late cutoff:** what time defines `LATE` vs `ON_TIME`, and per-leg missed-alert cutoffs?

---

## 7. Front-end prompt (paste into your front-end assistant)

> Build transport **boarding tracking** for a school system. Students are marked at the bus as they
> board/alight; each mark is a boarding event with a `leg` of `MORNING_PICKUP`, `MORNING_DROPOFF`,
> `EVENING_PICKUP`, or `EVENING_DROPOFF`. Students have a per-term `transportType` of `ONE_WAY`
> (morning legs only) or `TWO_WAY` (all four legs). The bus app must work **offline**.
>
> **Manual capture is the baseline and must fully work on its own** — a conductor taps students off
> the expected list (`method: "MANUAL"`, identified by `studentId`/`admissionNumber`). **Biometric/
> card/QR is an OPTIONAL add-on**, gated by a per-school flag `transportBiometricsEnabled`. When the
> flag is off, hide enrollment and scan mode and use manual only; when on, additionally offer them.
> Nothing in tracking/attendance/alerts depends on biometrics.
>
> **Do not handle raw biometric data.** When the add-on is on, the device SDK returns an opaque
> `biometricId` string; the app only ever sends that token. Un-enrolled students are still fully
> markable manually.
>
> Build these surfaces:
> 1. **Bus Capture App (mobile, offline-first):** choose vehicle + leg + date (default today, fixed
>    per device). Always show a **manual list** of students expected on this vehicle/leg; tapping a
>    name records a `MANUAL` event and checks them off. When `transportBiometricsEnabled` is on,
>    also offer a **scan mode** with strong success/failure feedback (colour + sound + student photo)
>    that falls back to the manual list on any failure. Maintain a **local queue** where each mark
>    gets a client-generated `eventId` (UUID) and the real device `capturedAt` (ISO-8601 with
>    timezone), auto-syncing to `POST api/v1/transport/boarding/scan` with body `{ events: [...] }`
>    when online. On the response, remove `accepted` and `duplicates` from the queue and show
>    `rejected` for review. Show pending-sync count and a live boarded/expected/missing counter.
>    Never block marking on network.
> 2. **Enrollment screen (admin) — only render when `transportBiometricsEnabled` is on:** search
>    student → capture biometric/card via device SDK → POST `{ studentId, biometricId, method,
>    deviceVendor }` to `api/v1/transport/biometric/enroll`. Enrollment is optional per student.
> 3. **Office tracking:** trip attendance from `GET api/v1/transport/boarding?vehicleId=&date=&leg=`
>    (render `boarded` with time+method and a highlighted `missing` list, plus the expected/boarded/
>    missing counts); and a per-student boarding timeline from
>    `GET api/v1/transport/boarding/student/{studentId}?from=&to=` where legs not expected for a
>    one-way student render as "n/a", not "missing".
>
> Each boarding event shape: `{ eventId, studentId | admissionNumber | biometricId, vehicleId, leg,
> method, capturedAt, deviceId, location? }` — send exactly one identifier per event (manual uses
> studentId/admissionNumber; scanner uses biometricId). The endpoint is idempotent on `eventId`, so
> resending a queued batch is safe.
>
> Add a settings toggle for parent notifications (on-mark vs missed-leg). Tag manual entries
> distinctly. Format times in the school's timezone.
