# Implementation Prompt: Integrate CBC PDF Report Card + Teacher-to-Subject Assignment (Frontend)

## Objective

Extend the existing EduePoa frontend to consume two new backend features:

1. **CBC PDF Report Card** — download a JasperReports-generated PDF report card for a learner.
2. **Teacher-to-Subject Assignment** — assign a teacher to one or many subjects across different
   grades/streams for an academic year, view and remove those assignments.

This is an extension of the current app. Inspect the existing frontend first (framework, HTTP client,
how the auth token and tenant id are attached, how permissions gate UI, how existing PDF/file
downloads and academics screens work) and build in the same style. Reuse existing components.

## Global conventions

- **Response envelope:** JSON endpoints return `{ "message": string, "statusCode": number, "entity": T }`.
  HTTP status equals `statusCode`; read data from `entity`. On error, `entity` may be null and
  `message` holds the reason — surface it as a toast.
- **Auth + tenant:** send the token and tenant identifier exactly as the current app does today.
- **Permissions:** gate every action/route with the existing permission mechanism (strings below).
  Handle `403` (no permission) and `404`/`409` gracefully.
- **Terms:** `termId` is `1 | 2 | 3` (→ TERM_1/2/3). `year` is a 4-digit integer, e.g. `2027`.

---

## Feature 1 — CBC PDF Report Card

### Endpoint

```
POST /api/v1/reports-j/cbc-report-card
Permission: report:generate
```

Request body (`ReportCardRequest`):

```jsonc
{
  "studentId": 100,          // required
  "termId": 1,               // required: 1|2|3
  "year": 2027,              // required
  "logoPath": null,          // optional; overrides tenant/default logo
  "schoolName": null,        // optional; overrides tenant branding
  "schoolMotto": null,       // optional
  "schoolContact": null      // optional
}
```

### Responses

- **Success (`200`):** the body is the **PDF binary** (`Content-Type: application/pdf`) with
  `Content-Disposition: attachment; filename=cbc-report-card-{studentId}-{year}.pdf`. There is **no**
  JSON envelope on success — handle it as a file/blob download.
- **Error (`400/500`):** the standard JSON envelope, e.g.
  `{ "message": "studentId, termId and year are required", "statusCode": 400, "entity": null }`.
- If no CBC results exist for the learner/term/year, the PDF still returns `200` and renders a
  built-in "No CBC results were found…" page (the empty state is inside the PDF, not a 404).

### Frontend behavior

- Add a **"Download Report Card (PDF)"** action on the learner/report screen. Collect `studentId`,
  `termId`, `year` (reuse the existing term/year selectors).
- Request the response as a **blob** (e.g. `responseType: 'blob'` / `Accept: application/pdf`), then
  trigger a browser download using the filename from `Content-Disposition` (fall back to
  `cbc-report-card-{studentId}-{year}.pdf`).
- **Important:** since success is a blob and errors are JSON, detect the content type. If the response
  is not `application/pdf` (e.g. an error), parse it as JSON and show `message` as a toast instead of
  downloading a broken file. If your HTTP client always parses blobs, read the blob as text and
  `JSON.parse` it on non-2xx.
- Show a loading state while generating (PDF fill can take a moment). Gate the button behind
  `report:generate`.

### Notes

- This is the **PDF** report. The earlier JSON report-card endpoint
  (`GET /api/v1/academics/reports/report-card/student/{studentId}?termId&year`) still exists and
  returns the structured `ReportCardDto` for on-screen rendering. Use the JSON endpoint to render the
  report in the UI, and this PDF endpoint for the downloadable/printable copy.

---

## Feature 2 — Teacher-to-Subject Assignment

A teacher can be assigned to **multiple subjects across different grades/streams**. Each assignment is
one row of (teacher, subject, grade, optional stream, year, optional term). Base path:
`/api/v1/academics/teacher-assignments`.

### 2.1 Assign one (`class:update`)

```
POST /api/v1/academics/teacher-assignments/assign
```

Request (`AssignTeacherToSubjectRequest`):

```jsonc
{
  "teacherId": 12,           // required (a User id)
  "academicSubjectId": 5,    // required (learning area / AcademicSubject id)
  "gradeId": 10,             // required
  "streamId": 3,             // optional; must belong to the grade
  "termId": 1,               // optional: 1|2|3 (null = all terms)
  "year": 2027               // required
}
```

Response `entity` on success (`201`) — `TeacherSubjectAssignmentDto`:

```jsonc
{
  "id": 900,
  "teacherId": 12,
  "teacherName": "Jane Doe",
  "academicSubjectId": 5,
  "subjectName": "Mathematics",
  "gradeId": 10,
  "gradeName": "Grade 7",
  "streamId": 3,             // null when grade-wide
  "streamName": "7A",        // null when grade-wide
  "termName": "TERM_1",      // null when term-agnostic
  "year": 2027,
  "active": true
}
```

Error statuses: `400` (missing/invalid fields, stream not in grade, bad termId),
`404` (teacher/subject/grade/stream not found), `409` (duplicate assignment) — all with a `message`.

### 2.2 Assign many at once (`class:update`)

```
POST /api/v1/academics/teacher-assignments/assign-bulk
```

Request (`BulkAssignTeacherRequest`):

```jsonc
{
  "teacherId": 12,
  "year": 2027,
  "assignments": [
    { "academicSubjectId": 5, "gradeId": 10, "streamId": 3, "termId": 1 },
    { "academicSubjectId": 5, "gradeId": 11, "streamId": 7 },
    { "academicSubjectId": 8, "gradeId": 10 }
  ]
}
```

Response `entity` (`BulkAssignResultDto`):

```jsonc
{
  "created": 2,
  "skipped": 1,
  "assignments": [ /* TeacherSubjectAssignmentDto[] that were created */ ],
  "skippedReasons": ["Already assigned: Mathematics / Grade 7 7A"]
}
```

Status: `201` if all created, **`206` if partial** (some created, some skipped), `409` if every item
was a duplicate, `400`/`404` for invalid input/teacher not found. Treat `206` as success and show a
summary using `created`/`skipped` and `skippedReasons`.

### 2.3 Remove / deactivate (`class:update`)

```
DELETE /api/v1/academics/teacher-assignments/{assignmentId}
```

Returns `200` with a confirmation message. This is a **soft delete** (`active=false`), so removed
assignments won't appear in active lists but history is preserved. Treat as idempotent from the UI.

### 2.4 Lookups (`class:read`)

```
GET /api/v1/academics/teacher-assignments/teacher/{teacherId}?year={year}
GET /api/v1/academics/teacher-assignments/grade/{gradeId}?year={year}
GET /api/v1/academics/teacher-assignments/subject/{subjectId}?year={year}
```

Each returns `entity: TeacherSubjectAssignmentDto[]` (shape as in 2.1). Use:
- **teacher/{id}** — a teacher's full workload across all subjects/classes (drive the teacher profile).
- **grade/{id}** — who teaches what in a grade.
- **subject/{id}** — all teachers of a subject.

### Frontend screens/behavior

- **Teacher Assignment screen** (gate assign/remove behind `class:update`, lists behind `class:read`):
  - Pick a teacher + year, then add multiple rows (subject + grade + optional stream + optional term)
    and submit via **assign-bulk**. Show a result summary (created/skipped + reasons).
  - Show the teacher's current assignments (from `teacher/{id}`), grouped by grade/subject, with a
    remove (deactivate) action per row.
- **Data sources for pickers:** teachers = existing users/staff list (teacher role); subjects =
  learning areas / `AcademicSubject` list already used elsewhere; grades and streams = existing grade/
  stream endpoints. Reuse the current selectors rather than inventing new ones.
- Validation before submit: teacher, subject, grade and year required; stream optional but must belong
  to the chosen grade (the backend also enforces this and returns `400`).
- Handle `409`/skipped-duplicate cleanly (inform, don't error out the whole flow).

---

## Models to add (or the app's equivalent)

- `ReportCardRequest` (Feature 1 request).
- `AssignTeacherToSubjectRequest`, `BulkAssignTeacherRequest` (+ nested item), `TeacherSubjectAssignmentDto`,
  `BulkAssignResultDto` (Feature 2).

## Acceptance

1. Clicking "Download Report Card (PDF)" downloads a valid PDF named
   `cbc-report-card-{studentId}-{year}.pdf`; a backend error shows the `message` as a toast rather than
   downloading a broken file.
2. Assigning a teacher to several subjects/classes in one bulk call reports created vs skipped
   accurately, and a duplicate is skipped (not fatal).
3. A teacher's assignment list shows all their subjects across different grades/streams.
4. Removing an assignment hides it from active lists.
5. All actions are permission-gated (`report:generate`, `class:update`, `class:read`) and degrade
   gracefully on 403/404/409.
