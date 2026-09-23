# Implementation Prompt: Switch the Frontend from Legacy "Subjects" to CBC "Learning Areas"

## Objective

The backend now treats CBC **Learning Areas** as the source of truth for "subjects." The legacy
`/api/v1/academics/subjects/**` endpoints are **deprecated** (still working, but emitting a
`Deprecation` header). Update the existing EduePoa frontend to read subjects from the learning-area
endpoints, and use the new `academicSubjectId` bridge field wherever a subject id is still required
(marks entry, teacher assignment, results).

This is an extension of the current app. Inspect it first (framework, HTTP client, how the token and
tenant id are attached, how permissions gate UI, and where subjects are currently fetched/used).
Reuse existing components; do not rebuild.

## Key concept

- A **Learning Area** (CBC catalogue entry) is the canonical "subject" going forward.
- Each active learning area is bridged to an operational subject; the bridge id is exposed as
  `academicSubjectId` on the learning-area payload.
- Endpoints that still key on a subject id (marks entry, teacher-to-subject assignment, CBC results)
  continue to accept the subject id — pass `learningArea.academicSubjectId` for that value.

So: **display and select learning areas; submit `academicSubjectId` where a subject id is expected.**

## Global conventions

- Response envelope: `{ "message": string, "statusCode": number, "entity": T }`. HTTP status equals
  `statusCode`; read from `entity`; show `message` on error.
- Send auth token + tenant id exactly as today.
- A `404` from these list endpoints with an empty `entity` means "no active curriculum version /
  nothing configured yet" — render an empty/onboarding state, not an error banner.

## What to change

### 1. Replace legacy subject calls

Find every place the frontend calls the legacy endpoints and migrate them:

| Legacy (deprecated) | Replacement |
|---|---|
| `GET /api/v1/academics/subjects/get-all` | `GET /api/v1/academics/curriculum/learning-areas` |
| `GET /api/v1/academics/subjects/grade/{gradeId}` | `GET /api/v1/academics/curriculum/grades/{gradeMappingId}/learning-areas` |
| `POST /api/v1/academics/subjects/create` | (see "custom subjects" below) |
| `DELETE /api/v1/academics/subjects/{subjectId}` | (curriculum config; not a routine action) |
| `GET /api/v1/academics/subjects/get-subject-per-student/{studentId}` | derive from learner's grade → learning areas |

Note: the grade path param for learning-areas is the **grade-mapping id** (the `id` from
`GET /api/v1/academics/curriculum/grades`), not the operational grade id.

If the app has a shared HTTP layer, treat any `Deprecation: true` response header as a signal to log a
console warning during the transition, so remaining legacy calls are easy to spot.

### 2. New / canonical endpoints (all permission `curriculum:read`)

```
GET /api/v1/academics/curriculum/learning-areas                          -> LearningAreaDto[]  (flat, all active)
GET /api/v1/academics/curriculum/grades                                  -> GradeDto[]         (id = grade-mapping id)
GET /api/v1/academics/curriculum/grades/{gradeMappingId}/learning-areas  -> LearningAreaDto[]
GET /api/v1/academics/curriculum/learning-areas/{id}                     -> LearningAreaDto
```

`LearningAreaDto` (note the new `academicSubjectId`):

```jsonc
{
  "id": 15,                       // learning-area id (curriculum)
  "catalogueKey": "MATHEMATICS",
  "officialName": "Mathematics",
  "displayName": "Mathematics",
  "code": null,
  "learningAreaType": "CORE",     // CORE | OPTIONAL | ACTIVITY_AREA | LANGUAGE | RELIGIOUS_EDUCATION | PATHWAY_SUBJECT
  "isCore": true,
  "isOptional": false,
  "status": "ACTIVE",
  "sequence": 3,
  "academicSubjectId": 42         // <-- use THIS as the subject id in marks/teacher-assignment/results
}
```

`GradeDto`:

```jsonc
{ "id": 9, "gradeId": 10, "gradeCode": "GRADE_7", "gradeName": "Grade 7",
  "educationLevel": "JUNIOR_SCHOOL", "sequence": 9 }
```

### 3. Use `academicSubjectId` where a subject id is required

The following existing endpoints still expect a subject id — supply the learning area's
`academicSubjectId`:

- Teacher assignment: `POST /api/v1/academics/teacher-assignments/assign` and `/assign-bulk` →
  `academicSubjectId` field.
- Marks/exam entry endpoints that take a `subject`/`subjectId`.
- CBC results are keyed by grade/term/year and return per-learning-area rows; no change needed to
  request them.

Recommended UI pattern: build a **subject picker** from `GET /curriculum/learning-areas` (or the
grade-scoped variant). Display `officialName`/`displayName`; store the selected item's
`academicSubjectId` as the value submitted to subject-id-based endpoints. If an item's
`academicSubjectId` is null (not yet bridged), disable it and show a hint to run curriculum
configuration.

### 4. Custom subjects

Do not use `POST /subjects/create` for new subjects. If the product needs a school-specific subject,
that is a curriculum-configuration action (a `SCHOOL_CUSTOM` learning area) — route it through the
curriculum management screens rather than the deprecated subject-create form. Keep the old create form
only if it is still needed for a legacy flow, and label it deprecated in the UI.

## Screens affected (typical)

- **Subject lists / dropdowns** anywhere in academics (marks entry, teacher assignment, timetable,
  reports filters) → source from learning areas.
- **Grade → subjects** views → use `grades/{gradeMappingId}/learning-areas`.
- Any "manage subjects" admin screen → point at curriculum management; mark legacy create/delete as
  deprecated or hide behind an admin/legacy flag.

## Models to add (or the app's equivalent)

- `LearningAreaDto` (with `academicSubjectId`), `GradeDto` — as above.
- Keep any existing `SubjectResponseDto` type only for the legacy screens still calling old endpoints
  during transition.

## Acceptance

1. All subject dropdowns/lists are populated from `/curriculum/learning-areas` (or the grade-scoped
   variant); no screen depends on `/subjects/get-all` for its primary data.
2. Selecting a learning area and submitting to marks entry / teacher assignment sends its
   `academicSubjectId`, and those flows work end-to-end.
3. Empty/`404` list responses render an onboarding/empty state, not an error.
4. Remaining calls to deprecated `/subjects/**` endpoints are identified (via the `Deprecation`
   header warning) and either migrated or explicitly flagged as legacy.
5. Everything remains permission-gated (`curriculum:read` for reads) and tenant-scoped as before.

## Notes

- The deprecated `/subjects/**` endpoints still return real data now (subjects are auto-created from
  learning areas on the backend), so nothing breaks during a phased migration.
- The structured on-screen report card (`GET /api/v1/academics/reports/report-card/student/{id}?...`)
  and the PDF report (`POST /api/v1/reports-j/cbc-report-card`) already use learning-area results;
  no change needed there.
