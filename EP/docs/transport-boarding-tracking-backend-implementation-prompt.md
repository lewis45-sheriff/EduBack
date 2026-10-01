# Transport Boarding Tracking — Backend Implementation Prompt

Implement server-side transport boarding tracking (attendance at the bus) for this Spring Boot,
multi-tenant school system (package root `com.EduePoa.EP`, module `com.EduePoa.EP.Transport`).
Students are marked as they board/alight the bus. **Manual capture is the baseline and must work on
its own; biometric/card/QR is an optional add-on** that produces the same boarding events via an
opaque token — never raw biometric data.

Follow the existing code conventions exactly (see "Conventions" below). Build against the same
patterns already used by `Transport`, `AssignTransport`, and `TransportTransactions`.

---

## 1. Domain model

### 1.1 Enums (new, in `com.EduePoa.EP.Transport`)

`BoardingLeg`:
- `MORNING_PICKUP` — boards at the stop toward school
- `MORNING_DROPOFF` — alights at school
- `EVENING_PICKUP` — boards at school toward home
- `EVENING_DROPOFF` — alights at the stop

`CaptureMethod`:
- `MANUAL` (baseline), `BIOMETRIC`, `NFC_CARD`, `QR`

`BoardingStatus`:
- `ON_TIME`, `LATE`, `MANUAL_OVERRIDE` (default `ON_TIME`; `LATE` computation is a later enhancement — see Open Questions)

Reuse the existing `TransportType` enum (`ONE_WAY` / `TWO_WAY`) already in the module.

### 1.2 Entity `TransportBoardingEvent` (table `transport_boarding_event`)
Extends `TenantScopedEntity`, `@Filter(name = "tenantFilter", ...)` like the other transport
entities. Fields:
- `Long id` (IDENTITY)
- `@ManyToOne(LAZY) Student student` — `student_id`, not null
- `@ManyToOne(LAZY) Transport vehicle` — `vehicle_id`, not null
- `LocalDate serviceDate` — `service_date`, not null (the school day the leg belongs to)
- `@Enumerated(STRING) BoardingLeg leg` — not null
- `@Enumerated(STRING) CaptureMethod method` — not null
- `@Enumerated(STRING) BoardingStatus status` — not null, default `ON_TIME`
- `LocalDateTime capturedAt` — not null (real device time, client-supplied)
- `@CreationTimestamp LocalDateTime recordedAt` — server insert time, not updatable
- `String deviceId` — nullable
- `Double latitude`, `Double longitude` — nullable (optional GPS)
- `String clientEventId` — `client_event_id`, nullable — the client-generated idempotency key

**Uniqueness / idempotency:** add a unique constraint on `(student_id, service_date, leg)` named
`uk_boarding_student_date_leg` — one event per student per leg per day. Also index
`client_event_id` (unique where non-null, or enforce dedupe in code) so a resent batch does not
duplicate. Add a normal index on `(vehicle_id, service_date, leg)` for manifest queries.

### 1.3 Entity `StudentTransportBiometric` (table `student_transport_biometric`) — optional add-on
Extends `TenantScopedEntity`, filtered. Maps a student to an opaque token. **Store no raw biometric
data.** Fields:
- `Long id`
- `@ManyToOne(LAZY) Student student` — `student_id`, not null
- `String biometricId` — the opaque token from the device SDK (or card UID / QR payload), not null
- `@Enumerated(STRING) CaptureMethod method` — `BIOMETRIC` / `NFC_CARD` / `QR`
- `String deviceVendor` — nullable
- `@CreationTimestamp LocalDateTime enrolledAt`

Unique constraint on `biometricId` (per tenant) named `uk_biometric_token`, and a unique on
`student_id` if a student may have at most one active token (confirm; otherwise allow many).

---

## 2. Repositories (extend `TenantAwareRepository`, like existing transport repos)

`TransportBoardingEventRepository`:
- `Optional<TransportBoardingEvent> findByStudentAndServiceDateAndLeg(Student student, LocalDate serviceDate, BoardingLeg leg)`
- `Optional<TransportBoardingEvent> findByClientEventId(String clientEventId)`
- `List<TransportBoardingEvent> findByVehicleAndServiceDateAndLeg(Transport vehicle, LocalDate serviceDate, BoardingLeg leg)`
- `List<TransportBoardingEvent> findByStudentIdAndServiceDateBetweenOrderByServiceDateAscLegAsc(Long studentId, LocalDate from, LocalDate to)`

`StudentTransportBiometricRepository`:
- `Optional<StudentTransportBiometric> findByBiometricId(String biometricId)`
- `Optional<StudentTransportBiometric> findByStudent(Student student)`

Reuse `AssignTransportRepository.findByVehicleAndTermAndYear(vehicle, term, year)` to compute the
expected roster (already exists).

---

## 3. Endpoints (controller `TransportBoardingController`, base `api/v1/transport/boarding`)

Return the existing `CustomResponse<?>` wrapper (statusCode/message/entity) like `TransportController`.
Annotate writes with `@Audit(module = "TRANSPORT", action = "...")` matching existing usage.
Secure with the permission model (section 6).

### 3.1 `POST api/v1/transport/boarding/scan` — record boarding events (single or batch)
Body: `{ "events": [ BoardingEventRequest, ... ] }`. `BoardingEventRequest`:
```
{
  "clientEventId": "uuid",                 // idempotency key (recommended)
  "studentId": 101,                        // exactly ONE of studentId / admissionNumber / biometricId
  "admissionNumber": "ADM123",
  "biometricId": "AF93...",
  "vehicleId": 5,
  "leg": "MORNING_PICKUP",
  "method": "MANUAL",                       // MANUAL for tap; BIOMETRIC/NFC_CARD/QR for scanner
  "capturedAt": "2026-09-29T06:42:11+03:00",
  "deviceId": "CONDUCTOR-PHONE-1",
  "latitude": -1.2921, "longitude": 36.8219 // optional
}
```
Processing per event (do NOT fail the whole batch on one bad event — return per-event outcome):
1. Resolve the student: by `studentId`, else `admissionNumber`, else look up `biometricId` via
   `StudentTransportBiometricRepository`. If none resolve → add to `rejected` with a reason.
2. Idempotency: if `clientEventId` present and already exists → `duplicates`. Else if an event for
   `(student, serviceDate, leg)` already exists → `duplicates` (update `capturedAt`/`method` only if
   you choose "last write wins"; default: treat as duplicate, no-op).
3. Derive `serviceDate` from `capturedAt` (date portion, school timezone). Validate `leg`, `method`,
   `vehicleId` (vehicle must exist). Persist a new `TransportBoardingEvent` with `status = ON_TIME`.
4. Add to `accepted`.

Response entity:
```
{
  "accepted":  [ { "clientEventId": "...", "eventId": 900, "studentId": 101, "status": "RECORDED" } ],
  "duplicates":[ { "clientEventId": "...", "status": "DUPLICATE" } ],
  "rejected":  [ { "clientEventId": "...", "reason": "Unknown identifier (student/biometricId not found)" } ]
}
```
The endpoint must be safe to call repeatedly with the same batch (offline sync resend).

### 3.2 `POST api/v1/transport/biometric/enroll` — map student to token (optional add-on)
Body: `{ studentId, biometricId, method, deviceVendor }`. Upsert: re-enrolling replaces the mapping.
Response entity: `{ id, studentId, biometricId, method, enrolledAt }`. Reject if `biometricId`
already maps to a different student.

### 3.3 `GET api/v1/transport/boarding?vehicleId=&date=&leg=` — trip manifest (expected vs actual)
- Expected roster = students from `AssignTransport` for that vehicle in the current term+year
  (resolve term via the existing `Term.getCurrentTerm()` / the date) whose `transportType` includes
  the requested `leg` (see leg-eligibility rule in section 4).
- Actual = `findByVehicleAndServiceDateAndLeg`.
- `missing = expected − actual` (by studentId).

Response entity:
```
{
  vehicleId, vehicleNumber, route, date, leg,
  expectedCount, boardedCount, missingCount,
  boarded: [ { studentId, admissionNumber, fullName, transportType, method, capturedAt, status } ],
  missing: [ { studentId, admissionNumber, fullName, transportType } ]
}
```

### 3.4 `GET api/v1/transport/boarding/student/{studentId}?from=&to=` — student history
Return events in the range grouped by `serviceDate`, each date listing the legs present (with time +
method + status). Legs not expected for the student's type render as absent-by-design (the front end
shows "n/a"); the backend just returns the events that exist plus the student's `transportType` so
the client can distinguish "not expected" from "missing".

---

## 4. Leg eligibility rule (which legs a student is expected on)

Given a student's per-term `transportType`:
- `TWO_WAY` → expected on all four legs.
- `ONE_WAY` → expected on `MORNING_PICKUP` and `MORNING_DROPOFF` only.

> **Open question (confirm before finalizing):** is `ONE_WAY` always morning-to-school, or can a
> one-way student be evening-only? If evening-only is possible, model direction on the assignment
> (e.g. add a `oneWayDirection` = MORNING/EVENING to `AssignTransport`) and derive eligible legs
> from it. Implement a single method `Set<BoardingLeg> eligibleLegs(AssignTransport)` so this rule
> lives in one place.

---

## 5. Optional-feature gating

Biometrics/card is optional per school. Do not hard-require enrollment anywhere:
- The `/scan` endpoint must accept `MANUAL` events identified by `studentId`/`admissionNumber` with
  **no** biometric mapping present. Biometric resolution is just one of three identifier paths.
- Enrollment endpoint (3.2) and biometric resolution are inert when unused.
- If a tenant-level flag is desired (e.g. `transportBiometricsEnabled`), read it from the existing
  tenant configuration (`com.EduePoa.EP.Multitenancy.entity.TenantConfiguration`) and use it only to
  gate the enroll endpoint / expose capability to the client. Tracking itself must never depend on
  it. Confirm the config source before wiring.

---

## 6. Security / permissions

Add new values to `com.EduePoa.EP.Authentication.Enum.Permissions` (do not reuse loosely):
- `TRANSPORT_BOARDING_MARK("transport_boarding:mark", "Record transport boarding events at the bus")`
- `TRANSPORT_BOARDING_READ("transport_boarding:read", "View transport boarding attendance and history")`
- `TRANSPORT_BIOMETRIC_ENROLL("transport_biometric:enroll", "Enrol a student's transport biometric/card token")`

Apply them on the endpoints the same way existing transport endpoints are secured
(e.g. `@PreAuthorize`/method security matching the codebase's current approach — mirror how
`vehicle:manage` / `transport:assign` are enforced). `/scan` requires `transport_boarding:mark`;
manifest + history require `transport_boarding:read`; enroll requires `transport_biometric:enroll`.
Remember `SchemaUpdater` already widens `role_permissions.permission` to VARCHAR(50) so new values
persist.

---

## 7. Multi-tenancy & data-integrity notes

- All new entities extend `TenantScopedEntity` and carry the `@Filter` so rows are tenant-isolated
  automatically — do not add manual tenant filtering in queries; the existing infrastructure handles it.
- `serviceDate` derivation must use a consistent timezone (Africa/Nairobi per current `Term` dates).
  Centralize it so `capturedAt` → `serviceDate` is deterministic across the batch.
- The unique constraint `(student_id, service_date, leg)` is the real idempotency guarantee; the
  `clientEventId` check is an optimization on top. Handle the DB unique-violation gracefully
  (catch → treat as duplicate) in case two device batches race.
- Hibernate `ddl-auto=update` will create the new tables/columns automatically; no manual DDL needed.
  If a legacy column type ever needs adjusting, follow the `SchemaUpdater` pattern.

---

## 8. Conventions to match (from existing code)

- DTOs use Lombok (`@Data` / `@Builder` / `@Getter/@Setter` + `@NoArgsConstructor/@AllArgsConstructor`),
  placed under `Transport/.../Request` and `Transport/.../Responses` like current transport DTOs.
- Service returns `CustomResponse<?>` (`com.EduePoa.EP.Utils.CustomResponse`) with `statusCode`,
  `message`, `entity`; controller does `ResponseEntity.status(response.getStatusCode()).body(response)`.
- Interface + `*Impl` split (`TransportBoardingService` / `TransportBoardingServiceImpl`),
  constructor injection via `@RequiredArgsConstructor`, `@Slf4j` logging.
- Enums serialized as STRING in JPA; if clients send non-canonical enum text, add a Jackson
  `@JsonCreator` tolerant parser like `TransportType` already has.
- Audit writes with `@Audit(module = "TRANSPORT", action = ...)` and `auditService.log(...)`.

---

## 9. Deliverables checklist

- [ ] Enums: `BoardingLeg`, `CaptureMethod`, `BoardingStatus`.
- [ ] Entities: `TransportBoardingEvent`, `StudentTransportBiometric` (both tenant-scoped, filtered).
- [ ] Repositories for both, plus reuse of `AssignTransportRepository` for the roster.
- [ ] Request/response DTOs (batch scan request, per-event outcome response, manifest, history, enroll).
- [ ] `TransportBoardingService` + `Impl`: idempotent batch ingest, manifest expected-vs-actual,
      student history, enrollment upsert, single `eligibleLegs(...)` rule.
- [ ] `TransportBoardingController` with the four endpoints, secured by the new permissions.
- [ ] New `Permissions` values.
- [ ] Compile clean (`./mvnw.cmd -o compile -DskipTests` from `EP/`) — fix any errors before done.
- [ ] Confirm the two open questions (one-way direction; biometrics flag source) or implement the
      documented defaults and note the assumption.
