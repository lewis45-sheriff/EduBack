# Implementation Prompt: Assign Subjects (Learning Areas) to a Class — One-by-One, Multi-Select, or All

## Objective

Extend the existing EduePoa frontend so an admin can assign subjects to a grade/class for an academic
year with a flexible selection mode:

- **One at a time** — pick a single subject and assign it.
- **Multi-select** — tick several subjects and assign them together.
- **All** — assign every subject in the system in one action.

This is an extension of the current app. Inspect it first (framework, HTTP client, how the auth token
and tenant id are attached, existing academics/class screens, and any current "assign subjects" UI).
Reuse existing components and styling; do not rebuild.

## Background (what changed on the backend)

The assign endpoint used to always assign **all** subjects. It now accepts an optional `subjectIds`
list that controls the mode. Subjects are CBC learning areas bridged to operational subjects — the id
you submit is the subject id, which you obtain from a learning area's `academicSubjectId`
(see the learning-areas migration). So the picker should list learning areas and submit their
`academicSubjectId` values.

## Global conventions

- Response envelope: `{ "message": string, "statusCode": number, "entity": T }`. HTTP status equals
  `statusCode`; read data from `entity`; show `message` (it carries useful detail like skipped ids).
- Send auth token + tenant id exactly as the current app does.
- A `404` with an explanatory `message` means "nothing found" (e.g. no subjects, or no assignments
  yet) — render an empty state, not an error banner.

## Endpoints

### Assign subjects to a grade

```
POST /api/v1/academics/classes/assign
```

Request body (`AssignSubjectsToGradeRequest`):

```jsonc
{
  "gradeId": 10,          // required (operational grade id)
  "year": 2027,           // required
  "subjectIds": [5, 8]    // optional; drives the mode (see below)
}
```

`subjectIds` semantics:
- `[5]` — assign a single subject (one-by-one).
- `[5, 8, 12]` — assign a chosen set (multi-select).
- `null`, omitted, or `[]` — assign ALL subjects in the system (backward-compatible default).

Each id is an `AcademicSubject` id — supply a learning area's `academicSubjectId`.

Response `entity` (`AssignmentResultDto`), status `200`:

```jsonc
{
  "gradeId": 10,
  "gradeName": "Grade 7",
  "year": 2027,
  "totalSubjectsAssigned": 2,          // subjects considered in this request
  "assignedSubjectNames": ["Mathematics", "Integrated Science"]
}
```

The `message` reports new vs. already-existing and any skipped invalid ids, e.g.:
`"1 new subject(s) assigned to Grade 7. 1 already existed. Skipped invalid subject id(s): [99]."`

Error responses (standard envelope):
- `404` — grade not found, no subjects in the system, or none of the provided ids existed.
- Duplicates are NOT errors: re-assigning an existing subject is silently skipped and counted.

### List subjects already assigned to a grade

```
GET /api/v1/academics/classes/{gradeId}/subjects?year={year}
```

Response `entity` (`SubjectResponseDto[]`), status `200`:

```jsonc
[
  { "id": 5, "subjectName": "Mathematics", "subjectCode": "MATHEMATICS",
    "learningArea": "MATHEMATICS", "isCbcCore": true }
]
```

`404` with a message when nothing is assigned yet for that grade/year — show an empty state.

### Source for the subject picker

Use the CBC learning areas as the list to choose from (they carry `academicSubjectId`):

```
GET /api/v1/academics/curriculum/learning-areas                          (all active)   [curriculum:read]
GET /api/v1/academics/curriculum/grades/{gradeMappingId}/learning-areas   (per grade)    [curriculum:read]
```

`LearningAreaDto` includes `officialName`/`displayName` (label) and `academicSubjectId` (value to
submit). If an item's `academicSubjectId` is null (not yet bridged), disable it and hint to run
curriculum configuration.

## UI behavior

On the class/grade detail screen (or a dedicated "Assign Subjects" dialog):

1. Select grade + year (reuse existing selectors).
2. Load the candidate subjects from learning areas; also load already-assigned subjects
   (`GET /classes/{gradeId}/subjects`) to show current state and pre-check/disable already-assigned
   ones.
3. Provide a selection control that supports all three modes:
   - a checkbox list of subjects (tick one or many),
   - a "Select all" toggle,
   - a per-row "Assign" quick action for one-by-one.
4. On submit:
   - If specific rows are checked → send `subjectIds: [<academicSubjectId>...]`.
   - If "Select all / assign all" is chosen → omit `subjectIds` (or send `[]`).
5. After success, surface the `message` (created vs. already-existing vs. skipped), and refresh the
   assigned-subjects list.

Edge handling:
- Never submit `undefined`/`null` inside `subjectIds`; only include valid numeric ids. Do not fire the
  request until a valid `gradeId` and `year` are set.
- Show skipped-invalid-id feedback from `message` if present.
- Keep the action idempotent from the user's view (re-assigning is safe).

## Permissions

Follow the existing gating. The assign/list endpoints currently follow the class/academic area; gate
the screen with the app's academics/class permission (e.g. `class:update` for assigning, `class:read`
for viewing) consistent with other academics screens, and the learning-area picker with
`curriculum:read`. Handle `403` gracefully.

## Models to add (or the app's equivalent)

- `AssignSubjectsToGradeRequest` (`gradeId`, `year`, optional `subjectIds: number[]`).
- `AssignmentResultDto`, `SubjectResponseDto` — as above.
- Reuse `LearningAreaDto` (with `academicSubjectId`) for the picker.

## Acceptance

1. Admin can assign a single subject, a multi-selected set, or all subjects to a grade for a year from
   one screen.
2. Selected learning areas submit their `academicSubjectId` as `subjectIds`; "all" omits the field.
3. The success `message` (new / already existed / skipped) is shown, and the assigned list refreshes.
4. Already-assigned subjects are indicated and re-assigning them is safe (no error).
5. Empty/`404` responses render empty states; everything is permission-gated and tenant-scoped.
