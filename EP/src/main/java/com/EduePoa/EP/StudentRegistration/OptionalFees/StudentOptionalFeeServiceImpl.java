package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfigRepository;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.Parents.Parent;
import com.EduePoa.EP.Parents.ParentRepository;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeAssignRequest;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeBatchAssignRequest;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Response.StudentOptionalFeeResponseDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentGuardian;
import com.EduePoa.EP.StudentRegistration.StudentGuardianRepository;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for assigning, listing and removing optional fee items for students.
 * <p>
 * An optional item is a fee-structure line item ({@link FeeComponentConfig})
 * flagged {@code optional}. The assignment snapshots that line item's amount.
 * <p>
 * Authorization is enforced in two layers: controller-level {@code @PreAuthorize}
 * permission checks plus service-level resource-ownership checks. Parents must
 * additionally be linked to the target student through {@link StudentGuardian}
 * and may only assign line items flagged {@code parentAssignable}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentOptionalFeeServiceImpl implements StudentOptionalFeeService {

    private static final char ACTIVE = 'N';
    private static final char REMOVED = 'Y';
    private static final String PARENT_ROLE = "PARENT"; // authorities are ROLE_ + role name

    private final StudentOptionalFeeRepository optionalFeeRepository;
    private final FeeComponentConfigRepository feeComponentConfigRepository;
    private final StudentRepository studentRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceRepository financeRepository;
    private final AuditService auditService;

    @Override
    @Audit(module = "OPTIONAL FEE", action = "ASSIGN")
    public CustomResponse<?> assign(StudentOptionalFeeAssignRequest request) {
        CustomResponse<StudentOptionalFeeResponseDTO> response = new CustomResponse<>();
        try {
            // 1. Basic payload validation
            if (request == null || request.getStudentId() == null || request.getFeeComponentConfigId() == null) {
                return error(response, HttpStatus.BAD_REQUEST,
                        "studentId and feeComponentConfigId are required");
            }

            // 2. Resolve authenticated user (server-side, never trusted from client)
            User currentUser = getCurrentUser();

            // 3. Resolve the student (tenant filter guarantees same-tenant only)
            Student student = studentRepository.findById(request.getStudentId()).orElse(null);
            if (student == null) {
                return error(response, HttpStatus.NOT_FOUND,
                        "Student not found with ID: " + request.getStudentId());
            }

            // 4. Resolve the fee-structure line item (tenant filter enforces same tenant)
            FeeComponentConfig config = feeComponentConfigRepository.findById(request.getFeeComponentConfigId())
                    .orElse(null);
            if (config == null) {
                return error(response, HttpStatus.NOT_FOUND,
                        "Fee structure component not found with ID: " + request.getFeeComponentConfigId());
            }

            // 5. Line item must belong to a live fee structure
            FeeStructure feeStructure = config.getFeeStructure();
            if (feeStructure == null || feeStructure.getIsDeleted() == 'Y' || feeStructure.getDeleted() == 'Y') {
                return error(response, HttpStatus.BAD_REQUEST,
                        "The fee structure for '" + config.getName() + "' is not available");
            }

            // 6. Line item must be optional
            if (!config.isOptional()) {
                return error(response, HttpStatus.BAD_REQUEST,
                        "Fee component '" + config.getName()
                                + "' is a mandatory item and cannot be assigned as an optional fee");
            }

            // 7. Resolve the term from the line item; if the client supplied one it must match.
            Term term = parseConfigTerm(config.getTerm());
            if (term == null) {
                return error(response, HttpStatus.BAD_REQUEST,
                        "Fee component '" + config.getName() + "' has an invalid term: " + config.getTerm());
            }
            if (request.getTerm() != null && request.getTerm() != term) {
                return error(response, HttpStatus.BAD_REQUEST,
                        "Requested term " + request.getTerm().name() + " does not match the fee component term "
                                + term.name());
            }
            Year academicYear = request.getAcademicYear() != null ? request.getAcademicYear() : Year.now();

            // 8. Authorization — resolve actor type and enforce parent rules
            AssignedBy assignedBy = resolveActorAndAuthorize(currentUser, student, config, response);
            if (assignedBy == null) {
                return response; // response already populated
            }

            // 9. Duplicate active assignment protection
            boolean duplicate = optionalFeeRepository
                    .existsByStudent_IdAndFeeComponentConfig_IdAndTermAndAcademicYearAndIsDeleted(
                            student.getId(), config.getId(), term, academicYear, ACTIVE);
            if (duplicate) {
                return error(response, HttpStatus.CONFLICT,
                        "An active '" + config.getName() + "' optional fee already exists for this student for "
                                + term.name() + " " + academicYear);
            }

            // 10/11. Snapshot amount + record actor
            BigDecimal snapshotAmount = config.getAmount() != null ? config.getAmount() : BigDecimal.ZERO;

            StudentOptionalFee assignment = StudentOptionalFee.builder()
                    .student(student)
                    .feeComponentConfig(config)
                    .amount(snapshotAmount)
                    .term(term)
                    .academicYear(academicYear)
                    .assignedBy(assignedBy)
                    .assignedByUserId(currentUser.getId())
                    .isInvoiced('N')
                    .isDeleted(ACTIVE)
                    .build();

            // 12. Persist (tenant_id auto-populated by TenantEntityListener)
            StudentOptionalFee saved = optionalFeeRepository.save(assignment);

            // 13. Build response
            response.setEntity(toDto(saved));
            response.setMessage("Optional fee '" + config.getName() + "' assigned to "
                    + student.getFirstName() + " for " + term.name() + " " + academicYear
                    + " (amount: " + snapshotAmount + ")");
            response.setStatusCode(HttpStatus.CREATED.value());

            // 14. Audit
            auditService.log("OPTIONAL_FEE", "OPTIONAL_FEE_ASSIGNED student:",
                    String.valueOf(student.getId()), "component:", config.getName(),
                    "amount:", String.valueOf(snapshotAmount), "term:", term.name(),
                    "year:", String.valueOf(academicYear), "actor:", assignedBy.name(),
                    "userId:", String.valueOf(currentUser.getId()));

        } catch (RuntimeException e) {
            log.error("Failed to assign optional fee: {}", e.getMessage(), e);
            error(response, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return response;
    }

    @Override
    @Audit(module = "OPTIONAL FEE", action = "ASSIGN_BATCH")
    public CustomResponse<?> assignBatch(StudentOptionalFeeBatchAssignRequest request) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            if (request == null || request.getStudentId() == null
                    || request.getFeeComponentConfigIds() == null
                    || request.getFeeComponentConfigIds().isEmpty()) {
                return error(response, HttpStatus.BAD_REQUEST,
                        "studentId and at least one feeComponentConfigId are required");
            }

            List<StudentOptionalFeeResponseDTO> assigned = new ArrayList<>();
            List<Map<String, Object>> failed = new ArrayList<>();

            // Delegate each id to the single-assign path so all validation,
            // authorization, duplicate-protection and amount-snapshot rules apply
            // identically. Failures are collected per item rather than aborting the
            // whole batch (e.g. one duplicate does not block the rest).
            for (Long configId : request.getFeeComponentConfigIds()) {
                if (configId == null) {
                    continue;
                }
                StudentOptionalFeeAssignRequest single = new StudentOptionalFeeAssignRequest();
                single.setStudentId(request.getStudentId());
                single.setFeeComponentConfigId(configId);
                single.setTerm(request.getTerm());
                single.setAcademicYear(request.getAcademicYear());

                CustomResponse<?> itemResponse = assign(single);
                if (itemResponse.getStatusCode() != null
                        && itemResponse.getStatusCode() == HttpStatus.CREATED.value()) {
                    assigned.add((StudentOptionalFeeResponseDTO) itemResponse.getEntity());
                } else {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("feeComponentConfigId", configId);
                    failure.put("statusCode", itemResponse.getStatusCode());
                    failure.put("message", itemResponse.getMessage());
                    failed.add(failure);
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("requested", request.getFeeComponentConfigIds().size());
            summary.put("assignedCount", assigned.size());
            summary.put("failedCount", failed.size());
            summary.put("assigned", assigned);
            summary.put("failed", failed);

            response.setEntity(summary);
            // 207-style semantics on a single body: 201 when all succeeded,
            // 200 when some succeeded, 400 when none did.
            if (failed.isEmpty()) {
                response.setStatusCode(HttpStatus.CREATED.value());
                response.setMessage("Assigned " + assigned.size() + " optional fee(s)");
            } else if (!assigned.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("Assigned " + assigned.size() + " of "
                        + request.getFeeComponentConfigIds().size()
                        + " optional fee(s); " + failed.size() + " failed");
            } else {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("No optional fees were assigned; " + failed.size() + " failed");
            }

        } catch (RuntimeException e) {
            log.error("Failed to batch-assign optional fees: {}", e.getMessage(), e);
            error(response, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return response;
    }

    @Override
    @Audit(module = "OPTIONAL FEE", action = "REMOVE")
    public CustomResponse<?> remove(Long id) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            User currentUser = getCurrentUser();

            StudentOptionalFee assignment = optionalFeeRepository.findByIdAndIsDeleted(id, ACTIVE).orElse(null);
            if (assignment == null) {
                return error(response, HttpStatus.NOT_FOUND,
                        "Active optional fee assignment not found with ID: " + id);
            }

            // Parent callers may only remove assignments for their own children,
            // and only items that remain parent-assignable.
            if (isParent(currentUser)) {
                CustomResponse<Object> guardOnly = new CustomResponse<>();
                AssignedBy actor = resolveActorAndAuthorize(currentUser, assignment.getStudent(),
                        assignment.getFeeComponentConfig(), guardOnly);
                if (actor == null) {
                    response.setStatusCode(guardOnly.getStatusCode());
                    response.setMessage(guardOnly.getMessage());
                    response.setEntity(null);
                    return response;
                }
            }

            // If this assignment was already folded into a generated invoice, reverse
            // its amount from the invoice total/balance and the Finance rollup. The
            // paid amount is never touched.
            boolean reversed = false;
            if (assignment.getIsInvoiced() == 'Y') {
                reversed = reverseFromInvoice(assignment);
            }

            // Soft delete
            assignment.setIsDeleted(REMOVED);
            optionalFeeRepository.save(assignment);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(reversed
                    ? "Optional fee removed and " + assignment.getAmount() + " deducted from the invoice"
                    : "Optional fee assignment removed");
            response.setEntity(null);

            auditService.log("OPTIONAL_FEE", "OPTIONAL_FEE_REMOVED assignmentId:", String.valueOf(id),
                    "student:", String.valueOf(assignment.getStudent().getId()),
                    "component:", assignment.getFeeComponentConfig().getName(),
                    "reversedFromInvoice:", String.valueOf(reversed),
                    "userId:", String.valueOf(currentUser.getId()));

        } catch (RuntimeException e) {
            log.error("Failed to remove optional fee {}: {}", id, e.getMessage(), e);
            error(response, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> listForStudent(Long studentId, Term term, Year academicYear) {
        CustomResponse<List<StudentOptionalFeeResponseDTO>> response = new CustomResponse<>();
        try {
            if (studentId == null) {
                return error(response, HttpStatus.BAD_REQUEST, "studentId is required");
            }

            User currentUser = getCurrentUser();

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                return error(response, HttpStatus.NOT_FOUND, "Student not found with ID: " + studentId);
            }

            // Parents may only view their own child's assignments (prevents IDOR).
            if (isParent(currentUser)) {
                Parent parent = resolveParent(currentUser);
                if (parent == null || !guardianLinkExists(studentId, parent.getId())) {
                    return error(response, HttpStatus.FORBIDDEN,
                            "You are not authorized to view optional fees for this student");
                }
            }

            List<StudentOptionalFee> assignments;
            if (term != null && academicYear != null) {
                assignments = optionalFeeRepository
                        .findByStudent_IdAndTermAndAcademicYearAndIsDeleted(studentId, term, academicYear, ACTIVE);
            } else {
                assignments = optionalFeeRepository.findByStudent_IdAndIsDeleted(studentId, ACTIVE);
            }

            List<StudentOptionalFeeResponseDTO> dtos = assignments.stream()
                    .map(this::toDto)
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Optional fees retrieved successfully");
            response.setEntity(dtos);

        } catch (RuntimeException e) {
            log.error("Failed to list optional fees for student {}: {}", studentId, e.getMessage(), e);
            error(response, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return response;
    }

    // ---------------------------------------------------------------------
    // Authorization helpers
    // ---------------------------------------------------------------------

    /**
     * Determines whether the current user is an admin or a parent and enforces the
     * parent-specific guardian/parentAssignable rules. Returns the resolved
     * {@link AssignedBy}, or {@code null} after populating {@code response} with an
     * error when the caller is not authorized for this student/line item.
     */
    private AssignedBy resolveActorAndAuthorize(User currentUser, Student student,
                                                FeeComponentConfig config, CustomResponse<?> response) {
        if (!isParent(currentUser)) {
            // Non-parent callers with the required permission act as ADMIN.
            return AssignedBy.ADMIN;
        }

        // Parent path — verify the authenticated parent's link to this student.
        Parent parent = resolveParent(currentUser);
        if (parent == null) {
            error(response, HttpStatus.FORBIDDEN,
                    "No parent profile is linked to the authenticated account");
            return null;
        }
        if (!guardianLinkExists(student.getId(), parent.getId())) {
            error(response, HttpStatus.FORBIDDEN,
                    "You are not authorized to assign optional fees to this student");
            return null;
        }
        // Item must be explicitly parent-assignable.
        if (!config.isParentAssignable()) {
            error(response, HttpStatus.FORBIDDEN,
                    "Fee component '" + config.getName() + "' cannot be assigned by a parent");
            return null;
        }
        return AssignedBy.PARENT;
    }

    private boolean guardianLinkExists(Long studentId, Long parentId) {
        Optional<StudentGuardian> link = studentGuardianRepository
                .findByStudent_IdAndParent_Id(studentId, parentId);
        return link.isPresent();
    }

    private Parent resolveParent(User currentUser) {
        return parentRepository.findByUser_Id(currentUser.getId())
                .orElseGet(() -> currentUser.getEmail() != null
                        ? parentRepository.findByEmail(currentUser.getEmail()).orElse(null)
                        : null);
    }

    private boolean isParent(User user) {
        return user.getRole() != null && PARENT_ROLE.equalsIgnoreCase(user.getRole().getName());
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    /**
     * Deducts a removed optional-fee assignment's amount from the student's invoice
     * (total and balance) and the Finance rollup for the same term/year. The paid
     * amount is left untouched. Returns {@code true} if an invoice was adjusted.
     */
    private boolean reverseFromInvoice(StudentOptionalFee assignment) {
        BigDecimal amount = assignment.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        Long studentId = assignment.getStudent().getId();
        Term term = assignment.getTerm();
        Year year = assignment.getAcademicYear();

        Optional<StudentInvoices> invoiceOpt = studentInvoicesRepository
                .findByStudentAndTermAndAcademicYear(assignment.getStudent(), term, year);

        boolean adjusted = false;
        if (invoiceOpt.isPresent()) {
            StudentInvoices invoice = invoiceOpt.get();
            if (invoice.getIsDeleted() != 'Y') {
                BigDecimal newTotal = safe(invoice.getTotalAmount()).subtract(amount);
                // balance = total - amountPaid; recompute so paidAmount is preserved.
                BigDecimal newBalance = newTotal.subtract(safe(invoice.getAmountPaid()));
                invoice.setTotalAmount(newTotal);
                invoice.setBalance(newBalance);
                // Keep status consistent: a fully-paid invoice becomes Cleared.
                if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    invoice.setStatus('C');
                }
                studentInvoicesRepository.save(invoice);
                adjusted = true;
            }
        }

        financeRepository.findByStudentIdAndTermAndYear(studentId, term, year).ifPresent(finance -> {
            BigDecimal newTotal = safe(finance.getTotalFeeAmount()).subtract(amount);
            finance.setTotalFeeAmount(newTotal);
            finance.setBalance(newTotal.subtract(safe(finance.getPaidAmount())));
            finance.setLastUpdated(java.time.LocalDateTime.now());
            financeRepository.save(finance);
        });

        return adjusted;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ---------------------------------------------------------------------
    // Mapping / helpers
    // ---------------------------------------------------------------------

    /**
     * Parses the free-form term stored on a {@link FeeComponentConfig} into a
     * {@link Term}, tolerating both "TERM_1" and "TERM1" style values.
     */
    private Term parseConfigTerm(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace("TERM", "TERM_").replace("__", "_");
        try {
            return Term.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private StudentOptionalFeeResponseDTO toDto(StudentOptionalFee fee) {
        Student student = fee.getStudent();
        FeeComponentConfig config = fee.getFeeComponentConfig();
        Long feeStructureId = (config != null && config.getFeeStructure() != null)
                ? config.getFeeStructure().getId() : null;
        return StudentOptionalFeeResponseDTO.builder()
                .id(fee.getId())
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null
                        ? (student.getFirstName() + " " + student.getLastName()) : null)
                .feeComponentConfigId(config != null && config.getId() != null
                        ? Long.valueOf(config.getId()) : null)
                .feeComponentName(config != null ? config.getName() : null)
                .feeStructureId(feeStructureId)
                .amount(fee.getAmount())
                .term(fee.getTerm())
                .academicYear(fee.getAcademicYear())
                .assignedBy(fee.getAssignedBy())
                .assignedAt(fee.getAssignedAt())
                .status(fee.getIsDeleted() == ACTIVE ? "ACTIVE" : "REMOVED")
                .invoiced(fee.getIsInvoiced() == 'Y')
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> CustomResponse<T> error(CustomResponse<?> response, HttpStatus status, String message) {
        CustomResponse<T> typed = (CustomResponse<T>) response;
        typed.setEntity(null);
        typed.setMessage(message);
        typed.setStatusCode(status.value());
        return typed;
    }
}
