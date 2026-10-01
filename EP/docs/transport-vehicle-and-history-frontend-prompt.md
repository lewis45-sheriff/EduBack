# Frontend Integration Prompt — Vehicle-with-Students & Enriched Boarding History

Two additive backend changes are live. Neither alters existing fields, endpoints, permissions, request shapes, or the standard `{ message, statusCode, entity }` response wrapper — consumers that ignore the new fields keep working.

---

## 1. View a vehicle by id, including its assigned students

### Endpoint

```
GET /api/v1/transport/vehicle/{id}/students
```

Returns the vehicle plus the students currently assigned to it. Tenant-isolated. Returns `404` with `"Transport not found"` for an unknown id.

There is also the pre-existing `GET /api/v1/transport/vehicle/{id}` (vehicle only, with per-term prices). The new route is a superset for the "vehicle detail with roster" screen — use whichever fits the view.

### Response

```json
{
  "message": "Transport with assigned students retrieved successfully (2 student(s))",
  "statusCode": 200,
  "entity": {
    "id": 5,
    "vehicleNumber": "KDA 123A",
    "vehicleType": "Bus",
    "capacity": 33,
    "driverName": "John Kamau",
    "driverContact": "0712345678",
    "route": "Ruiru Route",
    "status": "active",
    "assignedCount": 2,
    "assignedStudents": [
      {
        "assignmentId": 12,
        "studentId": 101,
        "admissionNumber": "ADM101",
        "fullName": "Alice Doe",
        "pickupLocation": "Ruiru Stage",
        "transportType": "TWO_WAY",
        "term": "TERM_3",
        "year": 2026,
        "assignmentDate": "2026-09-01"
      },
      {
        "assignmentId": 13,
        "studentId": 102,
        "admissionNumber": "ADM102",
        "fullName": "Bob Roe",
        "pickupLocation": "Kamakis",
        "transportType": "ONE_WAY",
        "term": "TERM_3",
        "year": 2026,
        "assignmentDate": "2026-09-01"
      }
    ]
  }
}
```

Notes:
- `assignedStudents` is never `null` — an empty array when the vehicle has no assignments.
- `assignedCount` equals `assignedStudents.length` (convenience for headers/badges).
- `capacity` is the vehicle's seat capacity; compare against `assignedCount` to show utilization (e.g. "2 / 33").
- The roster currently covers all assignments for the vehicle across terms/years. (If you need it scoped to a single term/year, ask backend to add optional `term`/`year` query params.)

### Suggested TypeScript types

```ts
type TransportType = "ONE_WAY" | "TWO_WAY";
type Term = "TERM_1" | "TERM_2" | "TERM_3";

interface AssignedStudent {
  assignmentId: number;
  studentId: number;
  admissionNumber: string;
  fullName: string;
  pickupLocation: string;
  transportType: TransportType;
  term: Term;
  year: number;
  assignmentDate: string; // yyyy-MM-dd
}

interface VehicleWithStudents {
  id: number;
  vehicleNumber: string;
  vehicleType: string | null;
  capacity: number | null;
  driverName: string | null;
  driverContact: string | null;
  route: string | null;
  status: string | null;
  assignedCount: number;
  assignedStudents: AssignedStudent[];
}
```

### Suggested UI

- Vehicle detail header: `vehicleNumber`, `route`, `driverName`/`driverContact`, `status`.
- Utilization chip: `assignedCount / capacity`.
- Assigned-students table: Adm No., Name, Pickup Location, Type, Term/Year.

---

## 2. Enriched student boarding history

### Endpoint (unchanged shape, new fields)

```
GET /api/v1/transport/boarding/student/{studentId}?from={yyyy-MM-dd}&to={yyyy-MM-dd}
```

`from` and `to` must be ISO dates (`yyyy-MM-dd`). Non-date values return `400`. `from` must be on/before `to`; the range is capped (max 366 days).

Each event object now carries three additional fields:

- `latitude` (number | null) — GPS latitude captured at boarding, when the device supplied one.
- `longitude` (number | null) — GPS longitude captured at boarding.
- `recordedBy` (string | null) — username of the operator who recorded the event.

### Response

```json
{
  "message": "Transport boarding history retrieved successfully",
  "statusCode": 200,
  "entity": {
    "studentId": 1,
    "transportType": "TWO_WAY",
    "from": "2026-09-01",
    "to": "2026-09-29",
    "days": [
      {
        "serviceDate": "2026-09-29",
        "events": [
          {
            "leg": "MORNING_PICKUP",
            "capturedAt": "2026-09-29T06:42:11",
            "method": "MANUAL",
            "status": "ON_TIME",
            "latitude": -1.095123,
            "longitude": 36.998456,
            "recordedBy": "jane.driver@school.edu"
          }
        ]
      }
    ]
  }
}
```

Notes:
- All three new fields are nullable. Expect `null` for:
  - events recorded before this change went live (`recordedBy`, and coordinates if none were captured),
  - captures with no GPS fix (e.g. some `MANUAL` captures),
  - events created by system/automated flows (`recordedBy`).
- Only actual recorded events are returned; missing legs are never fabricated.

### Suggested TypeScript types

```ts
type BoardingLeg =
  | "MORNING_PICKUP" | "MORNING_DROPOFF"
  | "EVENING_PICKUP" | "EVENING_DROPOFF";

interface BoardingHistoryEvent {
  leg: BoardingLeg;
  capturedAt: string;   // ISO local date-time
  method: string;       // e.g. "MANUAL"
  status: string;       // e.g. "ON_TIME"
  latitude: number | null;
  longitude: number | null;
  recordedBy: string | null;
}

interface BoardingHistoryDay {
  serviceDate: string;  // yyyy-MM-dd
  events: BoardingHistoryEvent[];
}

interface BoardingHistory {
  studentId: number;
  transportType: TransportType;
  from: string;
  to: string;
  days: BoardingHistoryDay[];
}
```

### Suggested UI

- History timeline row per event: leg, time, method, status.
- Show "Recorded by {recordedBy}" when present; hide/label "—" when null.
- When `latitude`/`longitude` are present, render a small "View on map" link/pin (e.g. `https://maps.google.com/?q={latitude},{longitude}`); hide it when null.
