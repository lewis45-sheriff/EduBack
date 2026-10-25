package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentDecisionRequest;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentSubmitRequest;
import com.EduePoa.EP.Budget.DTO.Response.AmendmentResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Entity.BudgetAmendment;
import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Budget.Enum.AmendmentStatus;
import com.EduePoa.EP.Budget.Enum.AmendmentType;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Repository.BudgetAmendmentRepository;
import com.EduePoa.EP.Budget.Repository.BudgetCategoryRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnit;
import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import com.EduePoa.EP.OrgUnit.Repository.OrgUnitRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Submit / approve / reject / apply workflow for controlled amendments to an APPROVED budget
 * (design §9). Guarantees:
 * <ul>
 *   <li>Only APPROVED budgets can be amended; closed/draft are rejected.</li>
 *   <li>Self-approval is denied (the requester cannot approve their own amendment).</li>
 *   <li>Application is atomic and idempotent: the status guard + {@code @Version} optimistic lock
 *       ensure the same amendment can never be applied twice, even under concurrent approvals.</li>
 *   <li>The original approved baseline stays visible (line.originalAllocatedAmount is never changed
 *       by an amendment); only the current allocation moves.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BudgetAmendmentServiceImpl implements BudgetAmendmentService {

    private static final boolean DENY_SELF_APPROVAL = true;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final BudgetAmendmentRepository amendmentRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetCategoryRepository categoryRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------ SUBMIT

    @Override
    @Audit(module = "BUDGET", action = "AMEND")
    @Transactional
    public CustomResponse<?> submit(Long budgetId, AmendmentSubmitRequest request) {
        try {
            Budget budget = budgetRepository.findById(budgetId).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + budgetId);
            }
            if (budget.getStatus() != BudgetStatus.APPROVED) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only APPROVED budgets can be amended. Current status: " + budget.getStatus());
            }
            if (request.getAmount() == null || request.getAmount().signum() <= 0) {
                return status(HttpStatus.BAD_REQUEST, "amount must be a positive value");
            }

            BudgetAmendment.BudgetAmendmentBuilder builder = BudgetAmendment.builder()
                    .budget(budget)
                    .type(request.getType())
                    .reason(request.getReason())
                    .notes(request.getNotes())
                    .status(AmendmentStatus.PENDING)
                    .requestedBy(getCurrentUser())
                    .requestedAt(LocalDateTime.now());

            if (request.getType() == AmendmentType.SUPPLEMENTARY_LINE) {
                CustomResponse<?> err = buildSupplementary(budget, request, builder);
                if (err != null) {
                    return err;
                }
            } else {
                CustomResponse<?> err = buildDelta(budget, request, builder);
                if (err != null) {
                    return err;
                }
            }

            BudgetAmendment saved = amendmentRepository.save(builder.build());
            return ok(HttpStatus.CREATED, "Amendment submitted successfully", toResponse(saved));
        } catch (Exception e) {
            return fail(e);
        }
    }

    private CustomResponse<?> buildDelta(Budget budget, AmendmentSubmitRequest request,
                                         BudgetAmendment.BudgetAmendmentBuilder builder) {
        if (request.getBudgetLineId() == null) {
            return status(HttpStatus.BAD_REQUEST, "budgetLineId is required for an INCREASE/DECREASE amendment");
        }
        BudgetLine line = budgetLineRepository.findById(request.getBudgetLineId()).orElse(null);
        if (line == null || !line.getBudget().getId().equals(budget.getId())) {
            return status(HttpStatus.BAD_REQUEST, "Budget line not found for this budget: " + request.getBudgetLineId());
        }
        BigDecimal original = scale(line.getAllocatedAmount());
        BigDecimal delta = request.getType() == AmendmentType.DECREASE
                ? scale(request.getAmount()).negate()
                : scale(request.getAmount());
        BigDecimal proposed = original.add(delta);
        if (proposed.signum() < 0) {
            return status(HttpStatus.BAD_REQUEST,
                    "A DECREASE cannot reduce the allocation below zero (current " + original + ")");
        }
        builder.budgetLine(line)
                .category(line.getCategory())
                .orgUnit(line.getOrgUnit())
                .originalAmount(original)
                .requestedDelta(delta)
                .proposedAmount(proposed);
        return null;
    }

    private CustomResponse<?> buildSupplementary(Budget budget, AmendmentSubmitRequest request,
                                                 BudgetAmendment.BudgetAmendmentBuilder builder) {
        if (request.getBudgetLineId() != null) {
            return status(HttpStatus.BAD_REQUEST, "budgetLineId must be null for a SUPPLEMENTARY_LINE amendment");
        }
        if (request.getCategoryId() == null) {
            return status(HttpStatus.BAD_REQUEST, "categoryId is required for a SUPPLEMENTARY_LINE amendment");
        }
        BudgetCategory category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        if (category == null || category.getStatus() != CategoryStatus.ACTIVE) {
            return status(HttpStatus.BAD_REQUEST, "Active budget category not found: " + request.getCategoryId());
        }
        OrgUnit orgUnit = null;
        if (request.getOrgUnitId() != null) {
            orgUnit = orgUnitRepository.findById(request.getOrgUnitId()).orElse(null);
            if (orgUnit == null || orgUnit.getStatus() != OrgUnitStatus.ACTIVE) {
                return status(HttpStatus.BAD_REQUEST, "Active organizational unit not found: " + request.getOrgUnitId());
            }
        }
        if (lineExists(budget.getId(), category.getId(), orgUnit)) {
            return status(HttpStatus.CONFLICT,
                    "A line for that category and unit already exists; use INCREASE/DECREASE instead");
        }
        BigDecimal amount = scale(request.getAmount());
        builder.category(category)
                .orgUnit(orgUnit)
                .originalAmount(null)
                .requestedDelta(amount)
                .proposedAmount(amount);
        return null;
    }

    // ------------------------------------------------------------------ APPROVE

    @Override
    @Audit(module = "BUDGET", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approve(Long amendmentId, AmendmentDecisionRequest request) {
        try {
            BudgetAmendment amendment = amendmentRepository.findById(amendmentId).orElse(null);
            if (amendment == null) {
                return status(HttpStatus.NOT_FOUND, "Amendment not found with id: " + amendmentId);
            }
            if (amendment.getStatus() != AmendmentStatus.PENDING) {
                // Idempotent/guarded: an already-decided amendment cannot be applied again.
                return status(HttpStatus.BAD_REQUEST,
                        "Only PENDING amendments can be approved. Current status: " + amendment.getStatus());
            }
            Budget budget = amendment.getBudget();
            if (budget.getStatus() != BudgetStatus.APPROVED) {
                return status(HttpStatus.BAD_REQUEST,
                        "The target budget is no longer APPROVED (status " + budget.getStatus() + ")");
            }
            User approver = getCurrentUser();
            if (DENY_SELF_APPROVAL && amendment.getRequestedBy() != null
                    && amendment.getRequestedBy().getId() != null
                    && amendment.getRequestedBy().getId().equals(approver.getId())) {
                return status(HttpStatus.FORBIDDEN,
                        "The amendment requester cannot approve their own amendment (segregation of duties)");
            }

            LocalDateTime now = LocalDateTime.now();
            BigDecimal finalAmount;

            if (amendment.getType() == AmendmentType.SUPPLEMENTARY_LINE) {
                // Re-check uniqueness at apply time (another amendment may have created the line).
                if (lineExists(budget.getId(), amendment.getCategory().getId(), amendment.getOrgUnit())) {
                    return status(HttpStatus.CONFLICT,
                            "A line for that category and unit now exists; this supplementary amendment is stale");
                }
                finalAmount = scale(amendment.getProposedAmount());
                BudgetLine line = BudgetLine.builder()
                        .budget(budget)
                        .category(amendment.getCategory())
                        .orgUnit(amendment.getOrgUnit())
                        .allocatedAmount(finalAmount)
                        .originalAllocatedAmount(ZERO) // original baseline for a supplementary line is zero
                        .build();
                budgetLineRepository.save(line);
            } else {
                BudgetLine line = budgetLineRepository.findById(amendment.getBudgetLine().getId()).orElse(null);
                if (line == null) {
                    return status(HttpStatus.BAD_REQUEST, "The amended budget line no longer exists");
                }
                finalAmount = scale(line.getAllocatedAmount()).add(scale(amendment.getRequestedDelta()));
                if (finalAmount.signum() < 0) {
                    return status(HttpStatus.BAD_REQUEST, "Applying this amendment would make the allocation negative");
                }
                line.setAllocatedAmount(finalAmount);
                budgetLineRepository.save(line);
            }

            recalculateTotals(budget);
            budgetRepository.save(budget);

            amendment.setStatus(AmendmentStatus.APPROVED);
            amendment.setApprovedBy(approver);
            amendment.setFinalApprovedAmount(finalAmount);
            amendment.setDecidedAt(now);
            amendment.setAppliedAt(now);
            BudgetAmendment saved = amendmentRepository.save(amendment);

            return ok(HttpStatus.OK, "Amendment approved and applied successfully", toResponse(saved));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ------------------------------------------------------------------- REJECT

    @Override
    @Audit(module = "BUDGET", action = "REJECT")
    @Transactional
    public CustomResponse<?> reject(Long amendmentId, AmendmentDecisionRequest request) {
        try {
            BudgetAmendment amendment = amendmentRepository.findById(amendmentId).orElse(null);
            if (amendment == null) {
                return status(HttpStatus.NOT_FOUND, "Amendment not found with id: " + amendmentId);
            }
            if (amendment.getStatus() != AmendmentStatus.PENDING) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only PENDING amendments can be rejected. Current status: " + amendment.getStatus());
            }
            if (request == null || request.getReason() == null || request.getReason().isBlank()) {
                return status(HttpStatus.BAD_REQUEST, "A rejection reason is required");
            }
            amendment.setStatus(AmendmentStatus.REJECTED);
            amendment.setApprovedBy(getCurrentUser());
            amendment.setRejectionReason(request.getReason().trim());
            amendment.setDecidedAt(LocalDateTime.now());
            BudgetAmendment saved = amendmentRepository.save(amendment);
            return ok(HttpStatus.OK, "Amendment rejected", toResponse(saved));
        } catch (Exception e) {
            return fail(e);
        }
    }

    // --------------------------------------------------------------------- READ

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getById(Long amendmentId) {
        BudgetAmendment amendment = amendmentRepository.findById(amendmentId).orElse(null);
        if (amendment == null) {
            return status(HttpStatus.NOT_FOUND, "Amendment not found with id: " + amendmentId);
        }
        return ok(HttpStatus.OK, "Amendment fetched successfully", toResponse(amendment));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> history(Long budgetId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<AmendmentResponse> result = amendmentRepository.findByBudgetId(budgetId, pageable).map(this::toResponse);
        return ok(HttpStatus.OK, "Amendment history fetched successfully", result);
    }

    // ------------------------------------------------------------------ HELPERS

    private boolean lineExists(Long budgetId, Long categoryId, OrgUnit orgUnit) {
        return orgUnit == null
                ? budgetLineRepository.existsByBudgetIdAndCategoryIdAndOrgUnitIsNull(budgetId, categoryId)
                : budgetLineRepository.existsByBudgetIdAndCategoryIdAndOrgUnitId(budgetId, categoryId, orgUnit.getId());
    }

    private void recalculateTotals(Budget budget) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (BudgetLine line : budgetLineRepository.findByBudgetId(budget.getId())) {
            if (line.getCategory() != null && line.getCategory().getType() == CategoryType.INCOME) {
                income = income.add(line.getAllocatedAmount());
            } else {
                expense = expense.add(line.getAllocatedAmount());
            }
        }
        budget.setTotalIncomeAllocated(income);
        budget.setTotalExpenseAllocated(expense);
        budget.setUpdatedAt(LocalDateTime.now());
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private AmendmentResponse toResponse(BudgetAmendment a) {
        return AmendmentResponse.builder()
                .id(a.getId())
                .budgetId(a.getBudget() != null ? a.getBudget().getId() : null)
                .budgetLineId(a.getBudgetLine() != null ? a.getBudgetLine().getId() : null)
                .categoryId(a.getCategory() != null ? a.getCategory().getId() : null)
                .categoryName(a.getCategory() != null ? a.getCategory().getName() : null)
                .orgUnitId(a.getOrgUnit() != null ? a.getOrgUnit().getId() : null)
                .orgUnitName(a.getOrgUnit() != null ? a.getOrgUnit().getName() : null)
                .type(a.getType())
                .originalAmount(a.getOriginalAmount())
                .requestedDelta(a.getRequestedDelta())
                .proposedAmount(a.getProposedAmount())
                .finalApprovedAmount(a.getFinalApprovedAmount())
                .reason(a.getReason())
                .notes(a.getNotes())
                .rejectionReason(a.getRejectionReason())
                .status(a.getStatus())
                .requestedByName(fullName(a.getRequestedBy()))
                .approvedByName(fullName(a.getApprovedBy()))
                .requestedAt(a.getRequestedAt())
                .decidedAt(a.getDecidedAt())
                .appliedAt(a.getAppliedAt())
                .build();
    }

    private String fullName(User u) {
        if (u == null) {
            return null;
        }
        String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return name.isEmpty() ? u.getEmail() : name;
    }

    private BigDecimal scale(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2);
    }

    private CustomResponse<Object> ok(HttpStatus httpStatus, String message, Object entity) {
        CustomResponse<Object> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(entity);
        r.setStatusCode(httpStatus.value());
        return r;
    }

    private CustomResponse<Object> status(HttpStatus httpStatus, String message) {
        CustomResponse<Object> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(null);
        r.setStatusCode(httpStatus.value());
        return r;
    }

    private CustomResponse<Object> fail(Exception e) {
        CustomResponse<Object> r = new CustomResponse<>();
        if (e instanceof OptimisticLockException || e instanceof ObjectOptimisticLockingFailureException) {
            r.setMessage("The amendment or budget was modified concurrently. Please reload and try again.");
            r.setStatusCode(HttpStatus.CONFLICT.value());
        } else {
            r.setMessage(e.getMessage());
            r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        r.setEntity(null);
        return r;
    }
}
