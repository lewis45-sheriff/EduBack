# Reports Module — Front-End Integration Prompt

This document describes the Reports section (a sidebar tab), the permission that gates it,
every report and its exact parameters, the API contract, and a ready-to-paste prompt for
the front-end assistant.

---

## 1. Permission (sidebar tab)

Added to `com.EduePoa.EP.Authentication.Enum.Permissions`:

| Enum constant  | Permission string | Description                                                          |
|----------------|-------------------|----------------------------------------------------------------------|
| `REPORTS_VIEW` | `reports:view`    | Access the Reports sidebar tab and generate/download PDF reports     |

- Show the **Reports** sidebar item only when the user has `reports:view`; otherwise hide
  the tab and block its route.
- The **CBC Report Card** action additionally requires the existing `report:generate`
  permission — hide only that action when the user lacks it.

---

## 2. API contract

### 2.1 PDF reports (single endpoint)

```
GET /api/v1/reports-j/load/{type}
```

- Query params are strings; send only the ones a report needs.
- Do **not** send a tenant id — the tenant is resolved server-side from the auth context.
- Success: HTTP 200, `Content-Type: application/pdf`, `Content-Disposition: attachment`.
- Error: non-200 with JSON body `{ "message": string, "statusCode": number, "entity": null }`.

Because success is a binary PDF and errors are JSON, the client must branch on the response
`Content-Type` (or status) before treating the body as a PDF.

### 2.2 Report cards (JSON body)

```
POST /api/v1/reports-j/report-card        // term-performance report card
POST /api/v1/reports-j/cbc-report-card    // CBC report card (requires report:generate)
```

Body:
```json
{ "studentId": 724, "gradeId": 12, "termId": 3, "year": 2026 }
```
`termId` is the integer 1 | 2 | 3. `gradeId` is only needed by `/report-card`. Response is a
PDF on success, or the same JSON error shape on failure.

Term values for the `/load` endpoint accept `1|2|3` or `TERM_1|TERM_2|TERM_3` (the backend
normalizes `Term 1`, `term_1`, etc.). Year is a 4-digit integer, e.g. `2026`.

---

## 3. Report catalog

> Status legend:
> - **Ready** — verified working against the current schema.
> - **Legacy (hide/disable)** — the report template still targets an older fee-structure
>   schema (tables `component`, `term`, `fee_structure_selected_optionals`,
>   `student_payment_history`) that is not present in the current database, so it will not
>   return data yet. Keep these out of the UI (or behind a "coming soon"/disabled state)
>   until their queries are rebuilt on the backend.

| # | Report | `{type}` / endpoint | Required inputs | Status |
|---|--------|---------------------|-----------------|--------|
| 1 | Student Fee Statement | `GET /load/fee_statement` | Student, Term, Year | **Ready** |
| 2 | Term Performance Report | `GET /load/term_performance_report` | Student, Grade, Term, Year | **Ready** |
| 3 | Report Card (term performance) | `POST /report-card` | Student, Grade, Term, Year | **Ready** |
| 4 | CBC Report Card | `POST /cbc-report-card` | Student, Term, Year | **Ready** (needs `report:generate`) |
| 5 | Student Fee Structure (Year) | `GET /load/fee_structure_student` | Student, Year | Legacy (hide/disable) |
| 6 | Student Fee Structure (Term) | `GET /load/fee_structure_student_term` | Student, Term, Year | Legacy (hide/disable) |
| 7 | Grade Fee Structure | `GET /load/fee_structure` | Grade, Year | Legacy (hide/disable) |
| 8 | Parent Fee Statement | `GET /load/parent_fee_statement` | Parent, Year | Legacy (hide/disable) |
| 9 | Parent Fee Structure | `GET /load/parent_fee_structure` | Parent, Term | Legacy (hide/disable) |

Branding parameters (`Logo`, `SchoolName`, `SchoolAddress`, `SchoolMotto`, `SchoolContact`)
are injected server-side and must never be sent by the client.

### Input control mapping

| UI control | Sent as (query / body) | Reports |
|------------|------------------------|---------|
| Student picker (search: name + admission no) | `studentID` (`studentId` in body) | 1, 2, 3, 4, 5, 6 |
| Grade / class picker | `gradeId` | 2, 3, 7 |
| Parent picker | `parentId` | 8, 9 |
| Term selector (Term 1/2/3 → `1`/`2`/`3`) | `term` (`termId` in body) | 1, 2, 3, 4, 6, 9 |
| Academic year | `year` | 1, 2, 3, 4, 5, 6, 7, 8 |

### Example requests

```
GET  /api/v1/reports-j/load/fee_statement?studentID=724&term=3&year=2026
GET  /api/v1/reports-j/load/term_performance_report?studentID=724&gradeId=12&term=3&year=2026
POST /api/v1/reports-j/cbc-report-card        { "studentId": 724, "termId": 3, "year": 2026 }
```

---

## 4. Front-end prompt (paste into your front-end assistant)

> Build a **Reports** section for the app, surfaced as a sidebar tab.
>
> **Access control**
> - Render the "Reports" sidebar item and its route only when the current user has the
>   permission `reports:view`. Otherwise hide the tab entirely and guard the route.
> - The "CBC Report Card" action additionally requires `report:generate`; when the user
>   lacks it, disable/hide just that action, not the whole tab.
>
> **Page layout**
> - Show a grid of report cards, one per *Ready* report. Each card has a title, a one-line
>   description, and a "Generate" button.
> - Do NOT show the reports marked *Legacy* below (or render them visibly disabled with a
>   "Not available yet" tooltip). They target a schema not present on the backend yet.
> - Clicking "Generate" opens a parameter form (modal or side panel) that collects only the
>   inputs that report needs, validates them, then downloads/previews the PDF.
>
> **Ready reports** (title — endpoint — inputs):
> 1. Student Fee Statement — `GET /api/v1/reports-j/load/fee_statement` — Student, Term, Year
> 2. Term Performance Report — `GET /api/v1/reports-j/load/term_performance_report` —
>    Student, Grade, Term, Year
> 3. Report Card — `POST /api/v1/reports-j/report-card` (JSON body) — Student, Grade, Term, Year
> 4. CBC Report Card — `POST /api/v1/reports-j/cbc-report-card` (JSON body) — Student, Term, Year
>    (requires `report:generate`)
>
> **Legacy reports — keep hidden/disabled for now:** Student Fee Structure (Year),
> Student Fee Structure (Term), Grade Fee Structure, Parent Fee Statement, Parent Fee
> Structure. Do not call their endpoints from the UI yet.
>
> **Input controls**
> - Student: searchable select (label = student name + admission number, value = student id).
> - Grade: select of grades/classes (value = grade id).
> - Term: select Term 1 / Term 2 / Term 3, submitted as `1` / `2` / `3`.
> - Year: numeric year input (default to the current academic year), submitted as e.g. `2026`.
> - Disable "Generate" until every required input for the chosen report is filled.
>
> **Calling GET PDF reports (items 1 and 2)**
> - Call `GET /api/v1/reports-j/load/{type}` with the required params as query strings; omit
>   unused params; never send a tenant id. Send auth as the app already does. Request the
>   response as a Blob.
> - If the response is 200 and `Content-Type` is `application/pdf`: build an object URL from
>   the Blob and open it in a new tab (preview) and/or download it using the filename from
>   the `Content-Disposition` header, falling back to `<type>-report.pdf`. Revoke the object
>   URL afterward.
> - If the response is not a PDF (JSON error): parse `{ message, statusCode }` and show
>   `message` as an error toast.
>
> **Calling POST report cards (items 3 and 4)**
> - `POST` the JSON body `{ studentId, gradeId?, termId, year }` (`gradeId` only for
>   `/report-card`; `termId` is 1|2|3). Handle the PDF Blob / JSON error exactly as above.
>
> **UX**
> - Show a loading state on "Generate" while the request is in flight.
> - Keep the form open after a successful generate so the user can adjust params and rerun.
> - Surface backend error messages verbatim, e.g. "Student ID is required", "Invalid term
>   '...'", "Tenant context is required to generate a fee statement", "No fee record is
>   available for the selected term.", and generic "An error occurred: ...".
