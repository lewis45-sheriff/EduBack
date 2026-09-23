# Implementation Prompt: Migrate "Subjects" to CBC "Learning Areas" & Deprecate Legacy Subject Endpoints

## Context (read first — do not skip)

EduePoa has TWO parallel representations of "a subject":

1. **Legacy operational subject** — `AcademicSubject` (table `academic_subject`), exposed by
   `AcademicSubjectController` at `/api/v1/academics/subjects/**`. This is what the whole academic
   pipeline actually references today:
   - `StudentsScore.academicSubject`, `CbcGradeResult.academicSubject`,
     `ClassSubjectAssignment.academicSubject`, `TeacherSubjectAssignment.academicSubject`
   - `ExamService`, `UploadMarksServiceImpl`, `CbcCalculatorServiceImpl` all resolve/aggregate by `AcademicSubject`.
   - The `academic_subject` table is currently **empty** (that is why `/subjects/get-all` returns a
     "no data" 404), because the curriculum seeder does not populate it.

2. **CBC curriculum learning area** — `LearningArea` (table `cbc_learning_area`), seeded by
   `CurriculumSeeder` from `resources/curriculum/cbc-2024.json`, exposed (read-only) at
   `/api/v1/academics/curriculum/**`. It is versioned, tied to an education level, and owns the
   strand → sub-strand → learning-outcome hierarchy. `LearningArea` already has an
   `academicSubjectId` bridge column that is currently never populated.

**Goal:** make CBC **Learning Areas** the source of truth for "subjects," keep the existing
marks/results/teacher-assignment pipeline working by bridging each active Learning Area to an
`AcademicSubject`, expose learning-area-first endpoints, and **deprecate** the legacy
`/api/v1/academics/subjects/**` endpoints without breaking existing callers.

Do NOT delete `AcademicSubject` or its table — too many entities depend on it. This is a bridge +
deprecate migration, not a rip-and-replace.

## Guardrails

- Preserve the existing architecture: tenant scoping via `TenantScopedEntity` + `TenantAwareRepository`
  (each entity re-declares the `tenantFilter`), `CustomResponse<T>` envelope, `@PreAuthorize`
  permission checks, `ApplicationRunner`/`TenantContext` seeding pattern, `ddl-auto=update` for schema.
- Everything must remain multi-tenant and idempotent (safe to run on every startup).
- Do not invent curriculum facts; learning areas come from the existing seeded data.
- Compile and run tests after each step (`./mvnw -q -DskipTests compile`, then `./mvnw test`).

---

## Step 1 — Bridge Learning Areas → AcademicSubject during seeding

Extend `CurriculumSeeder` (or add a `SubjectBridgeSeeder` that runs after it, `@Order` > 30) so that,
per tenant, for every ACTIVE `LearningArea` of the ACTIVE curriculum version:

- Ensure a matching `AcademicSubject` exists (create if missing). Map:
  - `AcademicSubject.subjectName` = `LearningArea.officialName`
  - `AcademicSubject.subjectCode` = `LearningArea.code` (or `catalogueKey` if code is null)
  - `AcademicSubject.learningArea` = `LearningArea.catalogueKey` (keep the existing free-text column populated)
  - `AcademicSubject.isCbcCore` = `LearningArea.isCore`
- Set `LearningArea.academicSubjectId` to the linked `AcademicSubject.id` (persist the bridge).
- Idempotency: match on an existing `AcademicSubject` by `subjectName` (or by the already-set
  `academicSubjectId`) so repeated startups never create duplicates. Add
  `AcademicSubjectRepository.findBySubjectName(String)` / `existsBySubjectName(String)` if needed.
- Wrap in `TenantContext.setCurrentTenant(...) / clear()` like the other seeders, and call it from
  `TenantProvisioningService.provisionTenant(...)` so new tenants are bridged automatically.

After this step, `/subjects/get-all` returns real rows and marks/results/teacher-assignment keep
working with `AcademicSubject`, but each subject is now traceable to its CBC learning area.

## Step 2 — Learning-area-first read endpoints (the new canonical source)

The curriculum read API already exists and should be treated as the canonical "subjects" source going
forward:

```
GET /api/v1/academics/curriculum/grades                                  -> GradeDto[]  (id = grade-mapping id)
GET /api/v1/academics/curriculum/grades/{gradeMappingId}/learning-areas  -> LearningAreaDto[]
GET /api/v1/academics/curriculum/learning-areas/{id}                     -> LearningAreaDto
```

Add whatever is missing so a caller can list learning areas the way they used to list subjects:

- `GET /api/v1/academics/curriculum/learning-areas` — all active learning areas for the active version
  (flat list, for pickers). Permission `curriculum:read`. Return `LearningAreaDto[]`.
- Ensure `LearningAreaDto` includes `academicSubjectId` (the bridge) so the frontend can drive
  marks/teacher-assignment (which still need the `AcademicSubject` id) directly from a learning area.
  Add the field to `CurriculumDtos.LearningAreaDto` and populate it in `CurriculumServiceImpl`.

Do not duplicate endpoints that already exist — extend the DTO and add only the flat-list endpoint.

## Step 3 — Deprecate the legacy subject endpoints

In `AcademicSubjectController` (`/api/v1/academics/subjects/**`), mark the class and each handler
`@Deprecated` and add a deprecation signal without breaking callers:

- Add Javadoc `@deprecated Use /api/v1/academics/curriculum/learning-areas (CBC learning areas).`
- Add a `Deprecation` + `Link` response header on each legacy endpoint pointing to the replacement,
  e.g. `Deprecation: true` and `Link: </api/v1/academics/curriculum/learning-areas>; rel="successor-version"`.
  (Implement via a small `@ControllerAdvice`/interceptor or by setting headers in each method — pick
  whatever is least invasive in this codebase.)
- Keep the endpoints functional (they now return real data thanks to Step 1). Do NOT remove them.
- Also annotate `AcademicSubjectService`/impl methods `@Deprecated` and reference the learning-area
  service as the successor.
- Optionally, if OpenAPI/springdoc is present, mark them `@Operation(deprecated = true)` so they show as
  deprecated in the API docs (springdoc is already a dependency).

Legacy endpoints to deprecate (all under `/api/v1/academics/subjects`):
`POST /create`, `GET /get-all`, `GET /grade/{gradeId}`, `DELETE /{subjectId}`,
`GET /get-subject-per-student/{studentId}`.

## Step 4 — Point create flows at learning areas (optional, guarded)

Discourage ad-hoc subject creation now that subjects are derived from the CBC catalogue:

- Have `POST /api/v1/academics/subjects/create` still work but log a deprecation warning; prefer that
  new "subjects" come from configuring offered learning areas.
- If a school genuinely needs a custom subject, it should be represented as a `LearningArea` with
  `source = SCHOOL_CUSTOM` (existing enum) and then bridged by Step 1 — document this path rather than
  encouraging direct `AcademicSubject` creation.

## Step 5 — Do NOT change the pipeline FKs (yet)

`StudentsScore`, `CbcGradeResult`, `ClassSubjectAssignment`, and `TeacherSubjectAssignment` continue
to reference `AcademicSubject`. Because of the Step 1 bridge, resolving a learning area to its
`academicSubjectId` is enough to drive all of them. A future phase may migrate these FKs to
`LearningArea` directly, but that is out of scope here and must be a separate, carefully-planned change.

## Step 6 — Migration of existing data

If a tenant already has `academic_subject` rows created manually (pre-bridge):

- Match them to learning areas by name where possible and set `LearningArea.academicSubjectId` to the
  existing row instead of creating a duplicate.
- Never delete existing `academic_subject` rows referenced by historical `students_score` /
  `cbc_grade_result` records.
- Document any rows that could not be matched (leave them as-is; they remain usable).

## Step 7 — Tests & verification

- Unit/integration test: after seeding, every active `LearningArea` has a non-null `academicSubjectId`
  and a corresponding `AcademicSubject` exists; running the seeder twice creates no duplicates.
- Test that `/subjects/get-all` returns the bridged subjects (no longer a "no data" 404).
- Test that the flat `learning-areas` endpoint returns active areas with `academicSubjectId` set.
- Confirm existing marks/results/teacher-assignment tests still pass (no pipeline regression).
- Run `./mvnw test`.

## Acceptance criteria

1. CBC Learning Areas are the canonical "subjects"; each active learning area is bridged to an
   `AcademicSubject` via `academicSubjectId` (idempotent, per tenant, auto on tenant provisioning).
2. `LearningAreaDto` exposes `academicSubjectId`, and a flat
   `GET /api/v1/academics/curriculum/learning-areas` endpoint exists.
3. Legacy `/api/v1/academics/subjects/**` endpoints are marked `@Deprecated`, emit a `Deprecation`
   header pointing to the successor, still function, and are shown as deprecated in API docs.
4. No entity FKs were changed; marks entry, CBC results, and teacher assignment still work unchanged.
5. No `academic_subject` rows or curriculum definitions are deleted; historical records stay intact.
6. Build and full test suite pass.
