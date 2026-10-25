# Implementation Prompt: Teacher Portal — built afresh, reusing the admin-side logic

## Goal
Build a **new, self-contained teacher portal** where a logged-in teacher can, scoped to **only their own assignments/class**:
1. Enter and edit marks for the subject(s) they are assigned to, in the class(es) they are assigned to.
2. Mark attendance for the class they are the **class teacher** of.
3. Compute CBC results for a class they are responsible for.
4. View report forms (PDF) and a class-performance summary for their class.

Build it **fresh** (new teacher-scoped controller/service/pages), but **reuse the existing admin-side logic and entities** underneath — wrap them with teacher identity + ownership checks instead of duplicating business rules. Delete/replace the current broken `teacherAssessmentApi` wiring that points at non-existent endpoints.

---

## Reuse map: admin building block -> teacher feature
Each teacher capability must call the SAME underlying entity/service the admin uses, with a thin ownership layer added.

| Teacher feature | Reuse (already works on admin side) | What to add |
|---|---|---|
| List "my assignments" | `TeacherSubjectAssignmentRepository.findByTeacherIdAndYear` + `TeacherSubjectAssignmentDto` | Resolve teacher from JWT, not a path id |
| Load class roster | `StudentRepository.findByGradeStreamId` (stream) / `findByGradeId` (whole grade) | Filter out soft-deleted; attach existing scores |
| Enter/edit marks | `Scoring.StudentsScore` + `StudentsScoreRepository` (`existsByStudentAndGradeAndTermAndAcademicSubjectAndExamTypeAndYear`, `findByGradeAndTermAndYear`) | **Upsert** (admin's `UploadMarksServiceImpl` only inserts & skips existing — teacher needs edit); set `subjectTeacher`, `rawScore`+`maximumScore` |
| Mark attendance | `AttendanceService` (`markAttendance`, `getClassAttendance`) + `AttendanceController` logic | Class-teacher ownership check |
| Compute results | `CbcCalculatorServiceImpl.computeForGrade(gradeId, termId, year)` | Ownership check + permission gate (currently ungated) |
| Report form (PDF) | `ReportsController` `GET api/v1/reports-j/load/REPORT_CARD` & `POST /cbc-report-card`; `ReportCardService.getReportCard` | Ownership check |
| Class performance | `ReportCardService.listReportCards` + internal `RankingService`/`StudentTermSummary` | New read endpoint exposing per-learner overall score/level/position |

**Current-user resolution to copy verbatim:** `CompetencyAssessmentServiceImpl.resolveCurrentUser()` -> `SecurityContextHolder.getContext().getAuthentication().getName()` (email) -> `userRepository.findByEmail(...)`. Teacher/subjectTeacher is a **User**; class teacher is a **Staff** (`Grade.classTeacher` / `GradeStream.classTeacher`) — bridge User<->Staff explicitly.

---

## Backend — new teacher-scoped API (`api/v1/academics/assessments`)
New `TeacherAssessmentController` + `TeacherAssessmentService(+Impl)` + DTOs. All endpoints resolve the teacher from the JWT and verify ownership via `TeacherSubjectAssignmentRepository.isTeacherAuthorized(teacherId, gradeId, subjectId, year)` (this query already exists, currently unused). Violations return **403**, not 404.

1. `GET /my-assignments?year=` -> caller's assignments (reuse `findByTeacherIdAndYear` + `TeacherSubjectAssignmentDto`, add `assignmentId`, `termId`, `assessmentStatus`, `maxScore`). Default year = current.
2. `GET /{assignmentId}/roster?termId=&year=` -> verify ownership; roster from `StudentRepository.findByGradeStreamId`/`findByGradeId` (excluding soft-deleted), pre-filled from existing `StudentsScore` for that subject/term/year. Shape -> `AssignmentRoster { assignmentId, learningAreaName, gradeName, streamName, termName, year, maxScore, assessmentStatus, learners:[{studentId, studentName, admissionNumber, rawScore, maxScore}] }`.
3. `POST /{assignmentId}/scores` -> body `{scores:[{studentId, rawScore}]}`; verify ownership; **upsert** `StudentsScore` (find existing by student+grade+term+subject+examType+year, update it; else insert) with `subjectTeacher = currentUser`, `rawScore`+`maximumScore` set. Idempotent — re-saving edits, never duplicates. (This is the key improvement over admin's insert-only `uploadMarks`.)
4. `POST /{assignmentId}/submit` -> mark the class's scores for that subject/term as submitted.
5. `GET /my-class` -> the grade/stream the caller is class teacher of (bridge User->Staff->`Grade.classTeacher`/`GradeStream.classTeacher`), or empty if none.
6. `POST /{gradeId}/compute?streamId=&termId=&year=` -> verify caller is class teacher (or otherwise authorized) for the grade, then delegate to `CbcCalculatorServiceImpl.computeForGrade`.
7. `GET /{gradeId}/performance?streamId=&termId=&year=` -> verify ownership; per-learner overall score/level/position (reuse `ReportCardService`/`RankingService`/`StudentTermSummary`). New endpoint — nothing like it exists today.

**Exam type for marks:** teacher marks need an `examTypeId`. Either (a) let the roster/scores carry the active exam type, or (b) add an exam-type selector sourced from `ExamService` (`/api/v1/exams/types`). State which.

**Assessment status (`DRAFT|SUBMITTED|APPROVED|PUBLISHED|AMENDED`):** no backend entity persists this today. Recommend **deriving** it from `StudentsScore.resultStatus`/`resultApproved` rather than adding a new table, unless a formal approval workflow is required. Document the chosen mapping.

**Permissions:** gate marks with `exam:grade` (and/or `exam_mark:enter`); attendance with `attendance:mark`/`attendance:read`; compute with `exam:grade`/`grade:publish` (there is no `result:compute` today — add one only if you want a distinct gate, which needs a role-seed migration); reports/performance with `report:generate`.

---

## Backend — tighten the reused admin endpoints (minimal, non-breaking)
- `CbcCalculatorController.compute` and `UploadMarksController.create` currently have **no `@PreAuthorize`**. Leave admin behavior intact but ensure the **teacher path** goes through the new ownership-checked endpoints. Do not loosen anything.
- Attendance marking is gated only by `attendance:mark` with no ownership check; the new `/my-class` + a server-side ownership guard should back the teacher attendance page so it stops reconstructing the class on the client.

---

## Frontend — build the teacher portal fresh on the working endpoints
Teacher portal lives at `src/app/teacher` (auth via `useTeacherAuth`, teachers only; `TeacherSidebar` from `src/lib/teacherNavigation.tsx`, permission-gated). Rebuild the flows cleanly:

- **`src/lib/api/teacherAssessmentApi.ts`** — point at the NEW endpoints above; fix all payload/field shapes to the new DTOs.
- **My Assignments** (`/teacher/assignments`, `/[id]`) -> `GET /my-assignments`, open an assignment.
- **Marks** (`/teacher/assignments/[id]/marks`) -> `GET /{id}/roster`, `POST /{id}/scores`, `POST /{id}/submit`. Mirror the existing admin marks-entry UX (roster grid, search, per-row validation against max, save, review+submit). Missing marks show blank, not 0.
- **Attendance** (`/teacher/attendance`) -> `GET /my-class` then existing `POST /mark-attendance`; remove the client-side staff->stream reconstruction.
- **Compute + Reports + Performance** -> a class view with "Compute Results" (new compute endpoint), per-learner "View Report Form" (reuse `reportApi.downloadStudentReportCard` / cbc PDF), and a "Class Performance" table (new performance endpoint).
- **Navigation** — `teacherNavigation.tsx` entries gated by the right permissions, pointing at working routes only.

---

## Hard requirements / guardrails
- **Teacher identity from the JWT only.** Never authorize off a client-supplied teacher id.
- **Ownership before every mutation** (marks, attendance, compute) -> **403** on violation. All new queries stay tenant-scoped (Hibernate tenant filter already enforces this).
- **Reuse, don't fork:** call the existing `StudentsScore`, `Attendance`, `CbcCalculator`, `ReportCard`, `TeacherSubjectAssignment` logic. The only genuinely new business logic is the upsert-on-save and the class-performance read.
- **No fabricated data:** blanks for missing marks; performance levels/ranges from the framework/backend, never hardcoded.
- **Conventions:** `CustomResponse<T>` envelope, `@PreAuthorize("hasPermission(null,'<perm>')")`, `ApiEnvelope` on the frontend, match existing admin marks/attendance UX.

## Decisions to confirm before coding (recommended defaults in brackets)
1. Assessment status — derive from `StudentsScore.resultStatus` [**derive**], or add a persisted status table?
2. Compute permission — reuse `exam:grade`/`grade:publish` [**reuse**], or add `result:compute` (needs migration)?
3. Attendance scope — class teacher marks only their own class, admins any [**class-teacher-only**]?
4. "My class" — support a teacher who class-teaches multiple streams [**yes, support multiple**]?
5. Exam type on marks entry — add an exam-type selector from `ExamService` [**yes, selector**], or infer a single active type?

## Verification (Definition of Done)
- Backend compiles; `GET /my-assignments` returns the logged-in teacher's assignments (404 gone).
- Teacher loads a roster, saves marks (idempotent edit, not duplicate), submits — and gets **403** on an assignment not theirs.
- Class teacher marks attendance for their class; blocked from others'.
- Teacher computes results and opens a report form + class-performance table for their class.
- Frontend `tsc --noEmit` clean, `next build` compiles, pages work end-to-end against the running backend.
- Live smoke test as teacher `lewiskipkemoi5345@gmail.com` (password already reset) in the teacher portal.
