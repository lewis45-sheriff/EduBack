# Implementation Prompt: Redesign the EduePoa Frontend for the New CBC/CBE Academic Module

## 1. Objective

The EduePoa backend now has a full Kenyan CBC/CBE curriculum and assessment module. Redesign and
extend the **existing** EduePoa frontend so it consumes the new APIs and presents CBC concepts
correctly: curriculum browsing, framework-driven results, competency evidence, values, PCIs,
report cards, and optional ranking.

This is an extension of an existing app, **not** a greenfield rebuild. Do not throw away the current
frontend. Inspect it first, reuse its architecture (routing, state management, HTTP client, auth/token
handling, tenant handling, component library, styling), and add the CBC screens in the same style.

## 2. Non-negotiable rules

1. **Inspect before coding.** Identify the framework (React/Angular/Vue/etc.), the HTTP layer, how
   auth tokens and the tenant identifier are attached to requests, how permissions gate UI, and the
   existing academics/exams screens. Produce a short analysis before writing code.
2. **Reuse the existing API client and response envelope.** Every backend endpoint returns the same
   wrapper: `{ "message": string, "statusCode": number, "entity": T }`. HTTP status equals
   `statusCode`. Read data from `entity`. Do not invent a different envelope.
3. **Do not present ranking as a CBC requirement.** Show position only when the report card returns a
   non-null `position`/`rankingScope`. When null, render no rank at all.
4. **Do not hard-code performance thresholds or level-colours-to-scores in the UI.** Levels, labels,
   points and bands come from the backend. The UI only maps a returned `performanceLevelCode`/
   `broadLevel` to a colour/badge, never a score range to a level.
5. **Curriculum-stage awareness.** Report cards and result views must gracefully hide fields that are
   null for a stage (e.g. Pre-Primary may have no `overallPercentage`, no numeric scores). Render what
   is present; never show empty "0%" placeholders for absent data.
6. **Permission-gate every screen** using the existing permission mechanism, with the permission
   strings listed in section 6.
7. **Match existing UX conventions**: pagination, loading/empty/error states, toasts, form validation,
   and accessibility (labels, keyboard nav, colour contrast — levels must not rely on colour alone).

## 3. Required analysis (deliver before implementation)

- Frontend stack, folder structure, routing, state, and HTTP client.
- How the auth token and tenant id are attached to requests today.
- Where the current academics/exams/marks screens live and how they call the backend.
- How permissions currently gate routes/components.
- A component inventory: what is reusable (tables, forms, modals, badges) vs. what is new.
- A migration note: which existing screens (subjects, marks entry, CBC results) change vs. are added.

## 4. Screens to build / redesign

Group under an "Academics / CBC" area. Reuse existing layout shells.

1. **Curriculum Explorer** (read-only, `curriculum:read`)
   - Curriculum version selector; education levels; grades.
   - Drill-down: Grade → Learning Areas → Strands → Sub-Strands → Learning Outcomes (with inquiry
     questions). Lazy-load children per level.
   - Catalogue tabs: Core Competencies, Values, PCIs.
2. **Assessment / Marks Entry** (redesign of existing marks screen, `exam_mark:enter` / `cat_mark:enter`)
   - Enter raw score + maximum score per learner per learning area (the backend normalizes).
   - Support non-numeric methods (observation/rubric/completion) where the assessment defines them.
   - Show approval status (`DRAFT/SUBMITTED/APPROVED/PUBLISHED`).
3. **CBC Results** (redesign, `exam:read` / `report:generate`)
   - Trigger compute for a grade+term+year; show per-learner, per-learning-area results with the
     `performanceLevelLabel`/`performanceLevelCode`, `points`, and `normalizedScore`.
   - Do **not** show a single aggregate when `assessmentFrameworkCode = KJSEA` (no cross-subject total).
4. **Competency Evidence** (`competency:assess`)
   - Record/view evidence per learner per competency (level + comment), not derived from marks.
5. **Report Card** (`report:generate`)
   - Render the structured report: header, learning-area table, competency section, values section,
     attendance summary, comments; position only if present. Build it PDF-export-ready (layout that
     can later be printed/exported), but do not implement PDF now.
6. **School Curriculum Configuration** (admin, `curriculum:manage`) — optional this phase
   - Choose active grades, offered optional learning areas, ranking policy on/off + scope.

## 5. API reference (exact contracts from the backend)

Base path: `/api/v1/academics`. All responses are wrapped:
`{ "message": string, "statusCode": number, "entity": <payload> }`. Send the auth token and tenant id
exactly as the existing app does. Errors use the same wrapper with a 4xx/5xx `statusCode` and a
`message`; `entity` may be null.

### 5.1 Curriculum (read) — permission `curriculum:read`

```
GET /api/v1/academics/curriculum/versions
GET /api/v1/academics/curriculum/grades
GET /api/v1/academics/curriculum/grades/{gradeMappingId}/learning-areas
GET /api/v1/academics/curriculum/learning-areas/{id}
GET /api/v1/academics/curriculum/learning-areas/{id}/strands
GET /api/v1/academics/curriculum/strands/{id}/sub-strands
GET /api/v1/academics/curriculum/sub-strands/{id}/learning-outcomes
GET /api/v1/academics/curriculum/competencies
GET /api/v1/academics/curriculum/values
GET /api/v1/academics/curriculum/pcis
```

`entity` payloads (arrays unless noted):

```jsonc
// versions -> CurriculumVersionDto[]
{ "id": 1, "code": "CBC_2024", "name": "Kenya CBC/CBE Rationalised Curriculum",
  "version": "2024", "status": "ACTIVE", "source": "OFFICIAL_KICD", "description": "..." }

// grades -> GradeDto[]   (id = grade-level-mapping id; gradeId = operational Grade id or null)
{ "id": 9, "gradeId": 10, "gradeCode": "GRADE_7", "gradeName": "Grade 7",
  "educationLevel": "JUNIOR_SCHOOL", "sequence": 9 }

// grades/{id}/learning-areas -> LearningAreaDto[]
{ "id": 15, "catalogueKey": "MATHEMATICS", "officialName": "Mathematics", "displayName": "Mathematics",
  "code": null, "learningAreaType": "CORE", "isCore": true, "isOptional": false,
  "status": "ACTIVE", "sequence": 3 }

// learning-areas/{id} -> LearningAreaDto  (single object, same shape as above)

// learning-areas/{id}/strands -> StrandDto[]
{ "id": 40, "code": "MATH_G7_S1", "name": "Numbers", "description": null, "sequence": 1 }

// strands/{id}/sub-strands -> SubStrandDto[]
{ "id": 60, "code": "MATH_G7_S1_SS1", "name": "Whole Numbers", "description": null,
  "suggestedLessons": null, "sequence": 1 }

// sub-strands/{id}/learning-outcomes -> LearningOutcomeDto[]
{ "id": 80, "code": "MATH_G7_SLO1", "description": "Use place value ... in real life",
  "knowledge": null, "skills": null, "attitudes": null, "values": null, "sequence": 1 }

// competencies -> CompetencyDto[]
{ "id": 1, "code": "COMMUNICATION_COLLABORATION", "name": "Communication and Collaboration",
  "description": null, "sequence": 1 }

// values -> ValueDto[]
{ "id": 1, "code": "RESPECT", "name": "Respect", "description": null, "sequence": 3 }

// pcis -> PciDto[]
{ "id": 1, "code": "HEALTH_EDUCATION", "name": "Health Education", "category": "Health",
  "description": null, "sequence": 1 }
```

Note the drill-down uses the **id** returned by the parent call (grade-mapping id → learning-area id →
strand id → sub-strand id). If no active curriculum version is configured, list endpoints return
`statusCode: 404` with an empty `entity` and an explanatory `message` — render an empty/onboarding state.

### 5.2 CBC results — compute & read

```
POST /api/v1/academics/cbc/compute?gradeId={gradeId}&termId={termId}&year={year}
GET  /api/v1/academics/cbc/student/{studentId}/term/{termId}
```

- `gradeId` = operational Grade id. `termId` = 1 | 2 | 3. `year` = e.g. 2027.
- Both return `entity: CbcResultDto[]`:

```jsonc
{
  "id": 500,
  "studentId": 100,
  "studentName": "Test Learner",
  "subjectId": 5,                 // learning-area (AcademicSubject) id
  "subjectName": "Mathematics",
  "termName": "TERM_1",
  "year": 2027,
  "averageScore": 84.0,           // legacy numeric mirror of normalizedScore
  "cbcLevel": 4,                  // 1-4 broad level (4=Exceeding ... 1=Below)
  "cbcLabel": "Exceeding Expectation 2",
  "normalizedScore": 84.00,       // percentage the level was resolved from
  "performanceLevelCode": "EE2",  // framework fine level, e.g. KJSEA EE2
  "performanceLevelLabel": "Exceeding Expectation 2",
  "performanceAbbreviation": "EE2",
  "performancePoints": 7,         // e.g. KJSEA points; may be null
  "assessmentFrameworkCode": "KJSEA", // null on the student/term read endpoint
  "teacherComment": null
}
```

UI rules for this payload:
- Prefer `performanceLevelLabel`/`performanceLevelCode` + `performancePoints` when present; fall back
  to `cbcLabel`/`cbcLevel` when the framework fields are null (legacy rows).
- When `assessmentFrameworkCode = "KJSEA"`, render each learning area independently and **do not**
  compute or display a combined aggregate/total across learning areas.
- `compute` returns `200` with a `message` describing how many combinations were computed and which
  framework was used (or that a fallback was used). Surface that message as a toast.
- `404` means no scores found for that grade/term/year — show an empty state, not an error banner.

### 5.3 Report card — permission `report:generate`

```
GET /api/v1/academics/reports/report-card/student/{studentId}?termId={termId}&year={year}
```

`entity: ReportCardDto`:

```jsonc
{
  "schoolName": null,
  "academicYear": 2027,
  "termName": "TERM_1",
  "studentId": 100,
  "studentName": "Test Learner",
  "admissionNumber": "ADM-001",
  "gradeName": "Grade 7",
  "streamName": "7A",
  "learningAreas": [
    { "learningAreaId": 5, "learningAreaName": "Mathematics",
      "normalizedScore": 84.00, "performanceLevelCode": "EE2",
      "performanceLevelLabel": "Exceeding Expectation 2", "performancePoints": 7,
      "teacherComment": null, "teacherName": null }
  ],
  "competencies": [
    { "competencyId": 1, "competencyName": "Communication and Collaboration",
      "levelLabel": "Meeting Expectation", "comment": "Works well in groups" }
  ],
  "values": [
    { "valueId": 3, "valueName": "Respect", "observation": "Consistently courteous" }
  ],
  "attendance": { "daysPresent": 58, "daysAbsent": 2, "daysLate": 1, "daysExcused": 0 },
  "overallPercentage": null,        // null when framework does not aggregate (e.g. KJSEA)
  "overallPerformanceLabel": null,
  "position": null,                 // null unless a ranking policy is enabled
  "rankingScope": null,             // e.g. "WITHIN_STREAM" when ranking enabled
  "teacherOverallComment": null,
  "headteacherComment": null,
  "assessmentFrameworkCode": "KJSEA",
  "curriculumVersionCode": null
}
```

Report card rendering rules:
- Sections whose arrays are empty are hidden entirely.
- Show `overallPercentage`/`overallPerformanceLabel` only when non-null.
- Show `position` + a "Rank (scope)" label only when `position` is non-null; otherwise render nothing
  about ranking.
- Attendance block hidden if `attendance` is null.
- Build the layout so it can be exported to PDF later (print stylesheet / dedicated print view).

### 5.4 Enumerations to model in the frontend

- `termId`: `1 → TERM_1`, `2 → TERM_2`, `3 → TERM_3`.
- Broad levels (`cbcLevel`): `4 = Exceeding`, `3 = Meeting`, `2 = Approaching`, `1 = Below`.
- KJSEA fine levels (data-driven, do not hard-code ranges): `EE1, EE2, ME1, ME2, AE1, AE2, BE1, BE2`
  with points `8..1`. Treat as opaque codes returned by the API; map code→badge/colour only.
- Result status: `DRAFT, SUBMITTED, APPROVED, PUBLISHED, AMENDED`.
- Ranking scope: `NONE, WITHIN_STREAM, WITHIN_GRADE, BOTH`.

## 6. Permissions → route/feature gating

Use the existing permission checks with these strings:

- `curriculum:read` — Curriculum Explorer and all curriculum GETs.
- `curriculum:manage` — School Curriculum Configuration.
- `assessment:read` / `assessment:create` / `assessment:manage` — assessment screens (as they are added).
- `exam_mark:enter`, `cat_mark:enter`, `exam:grade` — marks entry (existing).
- `exam:read` — viewing results.
- `report:generate` — CBC results compute/read and report card.
- `competency:assess` — competency evidence entry.
- `result:amend` — amending published results (future).

Hide nav items and disable actions the user lacks permission for; also handle a `403` gracefully.

## 7. Component & state guidance

- Add typed models/interfaces for every DTO in section 5 (or the equivalent in the app's language).
- Centralize the CBC API calls in one service/module mirroring the existing HTTP client, returning the
  unwrapped `entity` and surfacing `message` for toasts/errors.
- Reusable pieces: a `PerformanceLevelBadge` (maps code/broad level → styled badge with accessible text),
  a curriculum tree/drill-down component, a results table, and a printable report-card view.
- Loading/empty/error states for every data view; optimistic UI only where the existing app already does.

## 8. Testing & acceptance

- Unit-test the DTO mappers and the `PerformanceLevelBadge` mapping.
- Component/integration tests for: curriculum drill-down, results table (KJSEA = no aggregate),
  report card (hides null sections, shows rank only when present).
- Acceptance:
  1. Curriculum Explorer drills Grade 7 → Mathematics → Numbers → Whole Numbers → the SLO + inquiry
     questions using only the returned ids.
  2. Computing results for a grade/term/year lists per-learning-area levels; no cross-subject aggregate
     appears for KJSEA.
  3. A report card renders learning areas, competencies, values, attendance and comments, hides null
     fields, and shows rank only when `position` is non-null.
  4. Every CBC route is permission-gated and degrades gracefully on 403/404.
  5. No performance thresholds are hard-coded in the frontend.

## 9. Out of scope for this phase

PDF generation, Senior School pathway selection UI, and full analytics dashboards. Keep the report-card
layout export-ready and the data models forward-compatible, but do not build these now.
