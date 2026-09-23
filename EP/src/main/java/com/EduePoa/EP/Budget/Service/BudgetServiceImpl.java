package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.DTO.Request.BudgetApprovalRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetClosureRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetLineRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetUpdateRequest;
import com.EduePoa.EP.Budget.DTO.Request.MonthlyAllocationRequest;
import com.EduePoa.EP.Budget.DTO.Response.BudgetCategoryResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetLineResponse;
import com.EduePoa.EP.Budget.DTO.Response.BudgetResponse;
import com.EduePoa.EP.Budget.DTO.Response.MonthlyAllocationResponse;
import com.EduePoa.EP.Budget.Entity.Budget;
import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Entity.BudgetLine;
import com.EduePoa.EP.Budget.Entity.BudgetLineMonthlyAllocation;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Repository.BudgetCategoryRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineMonthlyAllocationRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.Budget.Repository.BudgetRepository;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetLineMonthlyAllocationRepository monthlyAllocationRepository;
    private final BudgetCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    /**
     * When true, the budget creator may not approve their own budget.
     * Default deny self-approval (segregation of duties). See design §13.
     */
    private static final boolean DENY_SELF_APPROVAL = true;

    // ----------------------------------------------------------------- CREATE

    @Override
    @Audit(module = "BUDGET", action = "CREATE")
    @Transactional
    public CustomResponse<?> create(BudgetCreateRequest request) {
        try {
            LocalDate[] window = resolveWindow(request.getAcademicTerm(), request.getAcademicYear(),
                    request.getStartDate(), request.getEndDate());
            if (window == null) {
                return status(HttpStatus.BAD_REQUEST,
                        "Provide an academic term+year or an explicit start/end date range");
            }
            if (window[0].isAfter(window[1])) {
                return status(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
            }

            User currentUser = getCurrentUser();
            Budget budget = Budget.builder()
                    .name(request.getName().trim())
                    .description(request.getDescription())
                    .academicTerm(request.getAcademicTerm())
                    .academicYear(request.getAcademicYear())
                    .startDate(window[0])
                    .endDate(window[1])
                    .status(BudgetStatus.DRAFT)
                    .totalIncomeAllocated(BigDecimal.ZERO)
                    .totalExpenseAllocated(BigDecimal.ZERO)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();
            Budget savedBudget = budgetRepository.save(budget);

            if (request.getLines() != null) {
                for (BudgetLineRequest lineReq : request.getLines()) {
                    CustomResponse<?> lineResult = persistLine(savedBudget, lineReq);
                    if (lineResult != null) {
                        // Propagate the first validation failure (rolls back via exception).
                        throw new IllegalStateException(lineResult.getMessage());
                    }
                }
                recalculateTotals(savedBudget);
                savedBudget = budgetRepository.save(savedBudget);
            }

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget created successfully");
            response.setEntity(toResponse(savedBudget));
            response.setStatusCode(HttpStatus.CREATED.value());
            return response;
        } catch (IllegalStateException e) {
            return status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ----------------------------------------------------------------- UPDATE

    @Override
    @Audit(module = "BUDGET", action = "UPDATE")
    @Transactional
    public CustomResponse<?> update(Long id, BudgetUpdateRequest request) {
        try {
            Budget budget = budgetRepository.findById(id).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + id);
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only DRAFT budgets can be edited. Current status: " + budget.getStatus());
            }

            LocalDate[] window = resolveWindow(request.getAcademicTerm(), request.getAcademicYear(),
                    request.getStartDate(), request.getEndDate());
            if (window == null) {
                return status(HttpStatus.BAD_REQUEST,
                        "Provide an academic term+year or an explicit start/end date range");
            }
            if (window[0].isAfter(window[1])) {
                return status(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
            }

            budget.setName(request.getName().trim());
            budget.setDescription(request.getDescription());
            budget.setAcademicTerm(request.getAcademicTerm());
            budget.setAcademicYear(request.getAcademicYear());
            budget.setStartDate(window[0]);
            budget.setEndDate(window[1]);
            budget.setUpdatedAt(LocalDateTime.now());
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget updated successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ------------------------------------------------------------------- READ

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getById(Long id) {
        try {
            Budget budget = budgetRepository.findById(id).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + id);
            }
            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget fetched successfully");
            response.setEntity(toResponse(budget));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                                    BudgetStatus status, Year academicYear, Term academicTerm,
                                    LocalDate startDate, LocalDate endDate, String search) {
        try {
            Sort sort = Sort.by(
                    "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC,
                    StringUtils.hasText(sortBy) ? sortBy : "createdAt");
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<BudgetResponse> result = budgetRepository
                    .findByFilters(status, academicYear, academicTerm, startDate, endDate,
                            StringUtils.hasText(search) ? search : null, pageable)
                    .map(this::toResponseHeaderOnly);

            CustomResponse<Page<BudgetResponse>> response = new CustomResponse<>();
            response.setMessage("Budgets fetched successfully");
            response.setEntity(result);
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    @Override
    @Audit(module = "BUDGET", action = "DELETE")
    @Transactional
    public CustomResponse<?> delete(Long id) {
        try {
            Budget budget = budgetRepository.findById(id).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + id);
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only DRAFT budgets can be deleted. Current status: " + budget.getStatus());
            }
            for (BudgetLine line : budgetLineRepository.findByBudgetId(id)) {
                monthlyAllocationRepository.deleteByBudgetLineId(line.getId());
            }
            budgetLineRepository.deleteByBudgetId(id);
            budgetRepository.deleteById(id);

            return status(HttpStatus.OK, "Budget deleted successfully");
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ---------------------------------------------------------- LINE MGMT

    @Override
    @Audit(module = "BUDGET", action = "UPDATE")
    @Transactional
    public CustomResponse<?> addLine(Long budgetId, BudgetLineRequest request) {
        try {
            Budget budget = budgetRepository.findById(budgetId).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + budgetId);
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Lines can only be added to a DRAFT budget. Current status: " + budget.getStatus());
            }
            CustomResponse<?> validation = persistLine(budget, request);
            if (validation != null) {
                return validation;
            }
            recalculateTotals(budget);
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget line added successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.CREATED.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    @Override
    @Audit(module = "BUDGET", action = "UPDATE")
    @Transactional
    public CustomResponse<?> updateLine(Long budgetId, Long lineId, BudgetLineRequest request) {
        try {
            Budget budget = budgetRepository.findById(budgetId).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + budgetId);
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Lines can only be edited on a DRAFT budget. Current status: " + budget.getStatus());
            }
            BudgetLine line = budgetLineRepository.findById(lineId).orElse(null);
            if (line == null || !line.getBudget().getId().equals(budgetId)) {
                return status(HttpStatus.NOT_FOUND, "Budget line not found for this budget: " + lineId);
            }
            if (request.getAllocatedAmount().signum() < 0) {
                return status(HttpStatus.BAD_REQUEST, "allocatedAmount must be zero or positive");
            }
            // Category change: guard against duplicates.
            if (!line.getCategory().getId().equals(request.getCategoryId())) {
                BudgetCategory category = categoryRepository.findById(request.getCategoryId()).orElse(null);
                if (category == null || category.getStatus() != CategoryStatus.ACTIVE) {
                    return status(HttpStatus.BAD_REQUEST, "Active budget category not found: " + request.getCategoryId());
                }
                if (budgetLineRepository.existsByBudgetIdAndCategoryId(budgetId, request.getCategoryId())) {
                    return status(HttpStatus.CONFLICT, "This budget already has a line for that category");
                }
                line.setCategory(category);
            }

            CustomResponse<?> phasing = applyMonthlyPhasing(line, request);
            if (phasing != null) {
                return phasing;
            }
            line.setAllocatedAmount(request.getAllocatedAmount());
            budgetLineRepository.save(line);

            recalculateTotals(budget);
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget line updated successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    @Override
    @Audit(module = "BUDGET", action = "UPDATE")
    @Transactional
    public CustomResponse<?> removeLine(Long budgetId, Long lineId) {
        try {
            Budget budget = budgetRepository.findById(budgetId).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + budgetId);
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Lines can only be removed from a DRAFT budget. Current status: " + budget.getStatus());
            }
            BudgetLine line = budgetLineRepository.findById(lineId).orElse(null);
            if (line == null || !line.getBudget().getId().equals(budgetId)) {
                return status(HttpStatus.NOT_FOUND, "Budget line not found for this budget: " + lineId);
            }
            monthlyAllocationRepository.deleteByBudgetLineId(lineId);
            budgetLineRepository.deleteById(lineId);

            recalculateTotals(budget);
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget line removed successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ---------------------------------------------------------- LIFECYCLE

    @Override
    @Audit(module = "BUDGET", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approve(Long id, BudgetApprovalRequest request) {
        try {
            Budget budget = budgetRepository.findById(id).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + id);
            }
            if (budget.getStatus() == BudgetStatus.APPROVED) {
                // Idempotent: do not duplicate approval or reset timestamps.
                CustomResponse<BudgetResponse> response = new CustomResponse<>();
                response.setMessage("Budget is already approved");
                response.setEntity(toResponse(budget));
                response.setStatusCode(HttpStatus.OK.value());
                return response;
            }
            if (budget.getStatus() != BudgetStatus.DRAFT) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only DRAFT budgets can be approved. Current status: " + budget.getStatus());
            }
            if (budgetLineRepository.findByBudgetId(id).isEmpty()) {
                return status(HttpStatus.BAD_REQUEST, "A budget must have at least one line before approval");
            }

            User approver = getCurrentUser();
            if (DENY_SELF_APPROVAL && budget.getCreatedBy() != null
                    && budget.getCreatedBy().getId() != null
                    && budget.getCreatedBy().getId().equals(approver.getId())) {
                return status(HttpStatus.FORBIDDEN,
                        "The budget creator cannot approve their own budget (segregation of duties)");
            }

            budget.setStatus(BudgetStatus.APPROVED);
            budget.setApprovedBy(approver);
            budget.setApprovedAt(LocalDateTime.now());
            budget.setApprovalComment(request != null ? request.getApprovalComment() : null);
            budget.setUpdatedAt(LocalDateTime.now());
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget approved successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    @Override
    @Audit(module = "BUDGET", action = "CLOSE")
    @Transactional
    public CustomResponse<?> close(Long id, BudgetClosureRequest request) {
        try {
            Budget budget = budgetRepository.findById(id).orElse(null);
            if (budget == null) {
                return status(HttpStatus.NOT_FOUND, "Budget not found with id: " + id);
            }
            if (budget.getStatus() == BudgetStatus.CLOSED) {
                // Idempotent.
                CustomResponse<BudgetResponse> response = new CustomResponse<>();
                response.setMessage("Budget is already closed");
                response.setEntity(toResponse(budget));
                response.setStatusCode(HttpStatus.OK.value());
                return response;
            }
            if (budget.getStatus() != BudgetStatus.APPROVED) {
                return status(HttpStatus.BAD_REQUEST,
                        "Only APPROVED budgets can be closed. Current status: " + budget.getStatus());
            }

            budget.setStatus(BudgetStatus.CLOSED);
            budget.setClosedBy(getCurrentUser());
            budget.setClosedAt(LocalDateTime.now());
            budget.setClosureComment(request != null ? request.getClosureComment() : null);
            budget.setUpdatedAt(LocalDateTime.now());
            Budget saved = budgetRepository.save(budget);

            CustomResponse<BudgetResponse> response = new CustomResponse<>();
            response.setMessage("Budget closed successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
            return response;
        } catch (Exception e) {
            return fail(e);
        }
    }

    // ------------------------------------------------------------- HELPERS

    /**
     * Resolve the authoritative [startDate, endDate] window from either an academic
     * term+year or an explicit date range. Returns null if neither basis is provided.
     */
    private LocalDate[] resolveWindow(Term term, Year year, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new LocalDate[]{startDate, endDate};
        }
        if (term != null && year != null) {
            LocalDate termStart = term.getStartDate().withYear(year.getValue());
            LocalDate termEnd = term.getEndDate().withYear(year.getValue());
            return new LocalDate[]{termStart, termEnd};
        }
        return null;
    }

    /**
     * Validate and persist a new line for the budget. Returns a failure CustomResponse
     * on validation error, or null on success.
     */
    private CustomResponse<?> persistLine(Budget budget, BudgetLineRequest request) {
        if (request.getAllocatedAmount() == null || request.getAllocatedAmount().signum() < 0) {
            return status(HttpStatus.BAD_REQUEST, "allocatedAmount must be zero or positive");
        }
        BudgetCategory category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        if (category == null || category.getStatus() != CategoryStatus.ACTIVE) {
            return status(HttpStatus.BAD_REQUEST, "Active budget category not found: " + request.getCategoryId());
        }
        if (budgetLineRepository.existsByBudgetIdAndCategoryId(budget.getId(), category.getId())) {
            return status(HttpStatus.CONFLICT, "This budget already has a line for category '" + category.getName() + "'");
        }

        BudgetLine line = BudgetLine.builder()
                .budget(budget)
                .category(category)
                .allocatedAmount(request.getAllocatedAmount())
                .build();
        BudgetLine savedLine = budgetLineRepository.save(line);

        CustomResponse<?> phasing = applyMonthlyPhasing(savedLine, request);
        if (phasing != null) {
            return phasing;
        }
        return null;
    }

    /**
     * Replace monthly phasing for a line, enforcing sum(monthly) <= allocatedAmount.
     * Returns a failure CustomResponse on validation error, or null on success.
     */
    private CustomResponse<?> applyMonthlyPhasing(BudgetLine line, BudgetLineRequest request) {
        // Clear existing phasing first.
        monthlyAllocationRepository.deleteByBudgetLineId(line.getId());

        List<MonthlyAllocationRequest> months = request.getMonthlyAllocations();
        if (months == null || months.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (MonthlyAllocationRequest m : months) {
            if (m.getAllocatedAmount() == null || m.getAllocatedAmount().signum() < 0) {
                return status(HttpStatus.BAD_REQUEST, "Monthly allocatedAmount must be zero or positive");
            }
            sum = sum.add(m.getAllocatedAmount());
        }
        if (sum.compareTo(request.getAllocatedAmount()) > 0) {
            return status(HttpStatus.BAD_REQUEST,
                    "Sum of monthly allocations (" + sum + ") exceeds the line allocation (" + request.getAllocatedAmount() + ")");
        }
        for (MonthlyAllocationRequest m : months) {
            BudgetLineMonthlyAllocation alloc = BudgetLineMonthlyAllocation.builder()
                    .budgetLine(line)
                    .monthValue(m.getMonth())
                    .yearValue(m.getYear())
                    .allocatedAmount(m.getAllocatedAmount())
                    .build();
            monthlyAllocationRepository.save(alloc);
        }
        return null;
    }

    /** Recompute persisted income/expense totals from the budget's active lines. */
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
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private BudgetResponse toResponse(Budget budget) {
        List<BudgetLineResponse> lines = new ArrayList<>();
        for (BudgetLine line : budgetLineRepository.findByBudgetId(budget.getId())) {
            List<MonthlyAllocationResponse> months = new ArrayList<>();
            for (BudgetLineMonthlyAllocation m : monthlyAllocationRepository.findByBudgetLineId(line.getId())) {
                months.add(MonthlyAllocationResponse.builder()
                        .id(m.getId())
                        .month(m.getMonthValue())
                        .year(m.getYearValue())
                        .allocatedAmount(m.getAllocatedAmount())
                        .build());
            }
            lines.add(BudgetLineResponse.builder()
                    .id(line.getId())
                    .categoryId(line.getCategory().getId())
                    .categoryName(line.getCategory().getName())
                    .categoryType(line.getCategory().getType())
                    .allocatedAmount(line.getAllocatedAmount())
                    .monthlyAllocations(months)
                    .build());
        }
        BudgetResponse response = toResponseHeaderOnly(budget);
        response.setLines(lines);
        return response;
    }

    private BudgetResponse toResponseHeaderOnly(Budget budget) {
        return BudgetResponse.builder()
                .id(budget.getId())
                .name(budget.getName())
                .description(budget.getDescription())
                .academicTerm(budget.getAcademicTerm())
                .academicYear(budget.getAcademicYear())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .status(budget.getStatus())
                .totalIncomeAllocated(budget.getTotalIncomeAllocated())
                .totalExpenseAllocated(budget.getTotalExpenseAllocated())
                .createdByName(fullName(budget.getCreatedBy()))
                .approvedByName(fullName(budget.getApprovedBy()))
                .closedByName(fullName(budget.getClosedBy()))
                .approvalComment(budget.getApprovalComment())
                .closureComment(budget.getClosureComment())
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .approvedAt(budget.getApprovedAt())
                .closedAt(budget.getClosedAt())
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

    private CustomResponse<BudgetResponse> status(HttpStatus httpStatus, String message) {
        CustomResponse<BudgetResponse> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(null);
        r.setStatusCode(httpStatus.value());
        return r;
    }

    private CustomResponse<BudgetResponse> fail(Exception e) {
        CustomResponse<BudgetResponse> r = new CustomResponse<>();
        if (e instanceof OptimisticLockException || e instanceof ObjectOptimisticLockingFailureException) {
            r.setMessage("The budget was modified by another user. Please reload and try again.");
            r.setStatusCode(HttpStatus.CONFLICT.value());
        } else {
            r.setMessage(e.getMessage());
            r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        r.setEntity(null);
        return r;
    }
}
