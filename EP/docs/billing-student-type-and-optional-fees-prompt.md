# Implementation Prompt: Student-Type Invoicing + Optional Fee Items

Use this prompt to implement two billing capabilities in the EduePoa (`com.EduePoa.EP`) Spring Boot multi-tenant backend. Follow the existing code structure and conventions exactly. Project root is the nested `EP/` folder: `EP/src/main/java/com/EduePoa/EP/`.

---

## Context: what already exists (do NOT rebuild)

- **Student-type invoicing is already partially built.** `FeeStructure` has a `FeeMode mode` (enum `DAY`, `BOARDING`). `FeeMode.fromBoardingStatus(BoardingStatus)` maps `DAY -> DAY` and `BOARDING`/`WEEKLY_BOARDING -> BOARDING`. `StudentInvoicesServiceImpl.create(...)` already resolves the structure via `feeStructureRepository.findByGradeAndModeAndYear(grade, feeMode, currentYear)` using the student's `boardingStatus`. Do not duplicate this — only extend/harden it per Part A.
- Invoice generation lives in `StudentInvoices/StudentInvoicesServiceImpl.java` (`create`, `invoiceAll`, read methods). Invoice entity `StudentInvoices` (status is a `char`: `'P'`/`'C'`/`'O'`; soft delete `isDeleted` char `'N'`).
- Fee structure `FeeStructure` (grade + year + `FeeMode` + `List<FeeComponentConfig> TermComponents`). Line items = `FeeComponentConfig` (`name`, `amount` BigDecimal, `term` String, `feeStatus` String). A reusable catalog also exists: `FeeComponents` (`type`, `category`, `term`, `amount`, `status`).
- `Finance/Finance.java` is a per-student per-term rollup (`studentId`, `term`, `year`, `totalFeeAmount`, `paidAmount`, `balance`). `create(...)` upserts it.
- Permissions: `Authentication/Enum/Permissions.java` (each constant has a `permission` string like `"invoice:create"` + description). Enforced via method-level `@PreAuthorize("hasPermission(null, 'xxx:yyy')")` resolving through `Authentication/Config/CustomPermissionEvaluator.java`, which checks `user.getRole().getRolePermissions()`. Existing perms include `INVOICE_CREATE`, `INVOICE_READ`, `FEE_STRUCTURE_MANAGE`, `FEE_COLLECT`, `FEE_READ`.
- Parents: `Parents/Parent.java` links to students through `StudentRegistration/StudentGuardian.java` (`student`, `parent`, `relationship`, `isPrimaryContact`, `isFeePayer`, `feeResponsibilityPercent`). Parents get a `ROLE_PARENT` role + `User` when portal access is enabled.
- Multi-tenancy: every entity extends `Multitenancy/base/TenantScopedEntity` and repeats the `@Filter(name="tenantFilter", condition="tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")` annotation. Repositories extend `Multitenancy/repository/TenantAwareRepository<T, ID>`.
- Conventions to match:
  - Controllers: `@RestController @RequestMapping("api/v1/<area>/") @RequiredArgsConstructor`, methods return `ResponseEntity.status(response.getStatusCode()).body(response)`.
  - Responses: generic wrapper `Utils/CustomResponse<T>` (`message`, `statusCode`, `entity`). Services return `CustomResponse<?>`, catch `RuntimeException` -> `INTERNAL_SERVER_ERROR`.
  - Services: `@Service @RequiredArgsConstructor` implementing an interface; constructor injection.
  - DTOs: response DTOs use Lombok `@Builder`; request DTOs plain.
  - Auditing: `@Audit(module=..., action=...)` + `auditService.log(...)`.

---

## Part A — Harden student-type (boarding vs day) invoicing

Goal: make the existing mode-based invoicing correct and explicit, not rebuild it.

1. In `StudentInvoicesServiceImpl.create(...)`, when `findByGradeAndModeAndYear` returns empty, keep the clear error but make the message explicitly state the student type + mode (e.g. "No BOARDING fee structure configured for grade X, year Y — configure it before invoicing boarding students").
2. Confirm `StudentInvoiceResponseDTO` surfaces the student type / fee mode. If absent, add a `feeMode` (or `boardingStatus`) field to `StudentInvoices/Responses/StudentInvoiceResponseDTO.java` and populate it in every builder site in `StudentInvoicesServiceImpl` (there are several — `create`, `getAllInvoices`, `getCurrentTermInvoices`, `getInvoicesByTerm`, `mapToStudentInvoiceResponseDTO`).
3. **Decision to confirm before coding:** `WEEKLY_BOARDING` currently collapses into `BOARDING` in `FeeMode.fromBoardingStatus`. If weekly boarders must be billed separately, add `WEEKLY_BOARDING` to the `FeeMode` enum and update the mapping + any `findByGradeAndMode*` seed data. Otherwise leave as-is and note the assumption. Do not change this silently.
4. Do not alter the `findByGradeAndModeAndYear` lookup contract or the `Finance` upsert.

---

## Part B — Optional fee items (assignable by parent or school admin, permission-gated)

Goal: introduce optional fee items that can be assigned per student and are included in that student's invoice total.

### B1. Catalog: mark fee items as optional vs mandatory
- Add a boolean/enum to distinguish optional items. Preferred: add `private boolean optional = false;` to `FeeComponents` (`FeeComponents/FeeComponents.java`). Keep it non-breaking (default `false`). Reuse the existing `FeeComponents` catalog rather than inventing a new catalog entity.
- Optionally add `private boolean parentAssignable = false;` so admins can control which optional items a parent (vs only staff) may self-assign.

### B2. New entity: `StudentOptionalFee` (per-student assignment)
Create package `StudentRegistration/OptionalFees/` (or `FeeComponents/Optional/` — match whichever sibling packaging is cleaner) with a tenant-scoped entity `StudentOptionalFee`:
- Extends `TenantScopedEntity`, carries the `@Filter` annotation, `@Entity`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, `@EqualsAndHashCode(callSuper = true)`.
- Fields: `Long id`; `@ManyToOne Student student`; `@ManyToOne FeeComponents feeComponent`; `BigDecimal amount` (snapshot at assignment time so later catalog price changes don't retro-alter issued invoices); `Term term` (enum `Authentication/Enum/Term`); `Year academicYear`; `String assignedByType` or an enum `AssignedBy {ADMIN, PARENT}`; `Long assignedByUserId`; a status/soft-delete consistent with siblings (`char isDeleted = 'N'` or `boolean deleted`); JPA auditing timestamps like `FeeComponentConfig`.
- Add a unique guard to prevent duplicate active assignments of the same component to the same student/term/year.

### B3. Repository
`StudentOptionalFeeRepository extends TenantAwareRepository<StudentOptionalFee, Long>` with finders: `findByStudent_IdAndTermAndAcademicYearAndIsDeleted(...)`, `findByStudent_Id(...)`, and an existence check for the uniqueness guard.

### B4. Service + interface
`StudentOptionalFeeService` (interface) + `StudentOptionalFeeServiceImpl` (`@Service @RequiredArgsConstructor`, returns `CustomResponse<?>`, try/catch `RuntimeException`, `@Audit` + `auditService.log`). Methods:
- `assign(...)` — validate the `FeeComponents` is `optional == true` (reject mandatory items); snapshot amount; enforce uniqueness; record who assigned it.
- `remove(...)` — soft delete (only if the invoice hasn't been finalized/paid for that term — reuse `Finance`/invoice state to decide).
- `listForStudent(studentId, term, year)`.
- **Parent-path authorization:** when the caller is a parent, verify via `StudentGuardian` that the authenticated parent is actually linked to that student before allowing assignment, and only permit `parentAssignable` items. Do not rely on the permission string alone for the parent path — combine permission + guardian-link check.

### B5. Controller
`StudentOptionalFeeController` — `@RestController @RequestMapping("api/v1/optional-fees/") @RequiredArgsConstructor`, returns `ResponseEntity.status(...).body(response)`. Endpoints: `assign`, `remove/{id}`, `student/{studentId}` (list). Gate each with `@PreAuthorize` (see B6).

### B6. Permissions
Add to `Authentication/Enum/Permissions.java`, matching the existing constant style (`NAME("area:action", "description")`):
- `OPTIONAL_FEE_ASSIGN("optional_fee:assign", "Assign optional fee items to a student")`
- `OPTIONAL_FEE_REMOVE("optional_fee:remove", "Remove an assigned optional fee item")`
- `OPTIONAL_FEE_READ("optional_fee:read", "View a student's assigned optional fee items")`
- (optional) `OPTIONAL_FEE_MANAGE_CATALOG("optional_fee:manage_catalog", "Mark catalog fee items as optional / parent-assignable")`

Apply `@PreAuthorize("hasPermission(null, 'optional_fee:assign')")` etc. on the controller methods (follow `Staff/StaffController` / `Grade/GradeController` pattern). Ensure the school-admin role and `ROLE_PARENT` role can be granted these where appropriate — assignment happens through `RolePermission`, so no hardcoding.

### B7. Integrate optional fees into invoice generation
In `StudentInvoicesServiceImpl.create(...)`, after computing `currentTermAmount` from `feeStructure.getTermComponents()`:
- Load active `StudentOptionalFee` for `(studentId, currentTerm, Year.of(currentYear))` and add their snapshot `amount`s to the term total **before** adding the carried-forward balance.
- Reflect optional-fee total in the response `message` and, if useful, add an `optionalFeesAmount` field to `StudentInvoiceResponseDTO`.
- Ensure `invoiceAll(...)` inherits this automatically (it calls `create`), so no separate change needed there.
- Keep the existing `Finance` upsert consistent with the new total.

---

## Verification (required before reporting done)
- Build: run the project's Maven build/compile (`./mvnw -q -DskipTests compile` or the wrapper present in the repo) and fix any errors.
- Confirm new entities create their tables via the existing `SchemaUpdater`/tenant schema flow (no manual DDL unless the project uses migrations — check first).
- Add/adjust tests only if the repo already has a test setup for billing; do not introduce a new framework unprompted.
- Manually trace one boarding student and one day student through `create(...)` with an assigned optional fee, confirming the totals differ by mode and include the optional item.

## Do NOT
- Do not change the `CustomResponse` shape, the `tenantFilter` condition, or the `char`-based invoice status.
- Do not add mandatory items to the optional-assignment path.
- Do not silently reclassify `WEEKLY_BOARDING` (see A3 — confirm first).
- Do not bypass the guardian-link check for the parent assignment path.
