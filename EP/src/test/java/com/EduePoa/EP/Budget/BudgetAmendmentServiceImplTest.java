package com.EduePoa.EP.Budget;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentDecisionRequest;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentSubmitRequest;
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
import com.EduePoa.EP.Budget.Service.BudgetAmendmentServiceImpl;
import com.EduePoa.EP.OrgUnit.Repository.OrgUnitRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Amendment workflow tests (design §9, §13): submission validation, atomic application that moves
 * the current allocation while preserving the original baseline, self-approval denial, idempotent
 * approval (cannot apply twice), and mandatory rejection reason.
 */
class BudgetAmendmentServiceImplTest {

    private BudgetAmendmentRepository amendmentRepository;
    private BudgetRepository budgetRepository;
    private BudgetLineRepository budgetLineRepository;
    private BudgetAmendmentServiceImpl service;

    private User requester;
    private User approver;

    @BeforeEach
    void setUp() {
        amendmentRepository = mock(BudgetAmendmentRepository.class);
        budgetRepository = mock(BudgetRepository.class);
        budgetLineRepository = mock(BudgetLineRepository.class);
        BudgetCategoryRepository categoryRepository = mock(BudgetCategoryRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        requester = new User();
        requester.setId(1L);
        requester.setEmail("maker@school.edu");
        approver = new User();
        approver.setId(2L);
        approver.setEmail("checker@school.edu");

        lenient().when(userRepository.findByEmail("maker@school.edu")).thenReturn(Optional.of(requester));
        lenient().when(userRepository.findByEmail("checker@school.edu")).thenReturn(Optional.of(approver));
        lenient().when(amendmentRepository.save(any(BudgetAmendment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new BudgetAmendmentServiceImpl(amendmentRepository, budgetRepository,
                budgetLineRepository, categoryRepository, orgUnitRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    private Budget approvedBudget() {
        Budget b = new Budget();
        b.setId(100L);
        b.setStatus(BudgetStatus.APPROVED);
        b.setTotalExpenseAllocated(new BigDecimal("1000.00"));
        b.setTotalIncomeAllocated(BigDecimal.ZERO);
        return b;
    }

    private BudgetLine expenseLine(Budget b) {
        BudgetCategory cat = BudgetCategory.builder()
                .id(10L).name("Salaries").type(CategoryType.EXPENSE).status(CategoryStatus.ACTIVE).build();
        return BudgetLine.builder().id(5L).budget(b).category(cat)
                .allocatedAmount(new BigDecimal("1000.00"))
                .originalAllocatedAmount(new BigDecimal("1000.00"))
                .build();
    }

    @Test
    @DisplayName("Submit INCREASE on an approved budget creates a PENDING amendment with correct proposed amount")
    void submitIncrease() {
        Budget b = approvedBudget();
        BudgetLine line = expenseLine(b);
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(b));
        when(budgetLineRepository.findById(5L)).thenReturn(Optional.of(line));
        authAs(requester);

        CustomResponse<?> r = service.submit(100L, AmendmentSubmitRequest.builder()
                .type(AmendmentType.INCREASE).budgetLineId(5L).amount(new BigDecimal("200.00"))
                .reason("More staff").build());

        assertThat(r.getStatusCode()).isEqualTo(201);
        var body = (com.EduePoa.EP.Budget.DTO.Response.AmendmentResponse) r.getEntity();
        assertThat(body.getOriginalAmount()).isEqualByComparingTo("1000.00");
        assertThat(body.getRequestedDelta()).isEqualByComparingTo("200.00");
        assertThat(body.getProposedAmount()).isEqualByComparingTo("1200.00");
        assertThat(body.getStatus()).isEqualTo(AmendmentStatus.PENDING);
    }

    @Test
    @DisplayName("Submit on a non-approved (DRAFT) budget is rejected")
    void submitOnDraftRejected() {
        Budget b = approvedBudget();
        b.setStatus(BudgetStatus.DRAFT);
        when(budgetRepository.findById(100L)).thenReturn(Optional.of(b));
        authAs(requester);

        CustomResponse<?> r = service.submit(100L, AmendmentSubmitRequest.builder()
                .type(AmendmentType.INCREASE).budgetLineId(5L).amount(new BigDecimal("200.00")).build());
        assertThat(r.getStatusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Requester cannot approve their own amendment (segregation of duties)")
    void selfApprovalDenied() {
        Budget b = approvedBudget();
        BudgetLine line = expenseLine(b);
        BudgetAmendment am = BudgetAmendment.builder()
                .id(7L).budget(b).budgetLine(line).type(AmendmentType.INCREASE)
                .originalAmount(new BigDecimal("1000.00")).requestedDelta(new BigDecimal("200.00"))
                .proposedAmount(new BigDecimal("1200.00")).status(AmendmentStatus.PENDING)
                .requestedBy(requester).build();
        when(amendmentRepository.findById(7L)).thenReturn(Optional.of(am));
        authAs(requester);

        CustomResponse<?> r = service.approve(7L, null);
        assertThat(r.getStatusCode()).isEqualTo(403);
        assertThat(am.getStatus()).isEqualTo(AmendmentStatus.PENDING); // unchanged
    }

    @Test
    @DisplayName("Approve applies the delta, moves current allocation, preserves original baseline")
    void approveApplies() {
        Budget b = approvedBudget();
        BudgetLine line = expenseLine(b);
        BudgetAmendment am = BudgetAmendment.builder()
                .id(7L).budget(b).budgetLine(line).type(AmendmentType.INCREASE)
                .originalAmount(new BigDecimal("1000.00")).requestedDelta(new BigDecimal("200.00"))
                .proposedAmount(new BigDecimal("1200.00")).status(AmendmentStatus.PENDING)
                .requestedBy(requester).build();
        when(amendmentRepository.findById(7L)).thenReturn(Optional.of(am));
        when(budgetLineRepository.findById(5L)).thenReturn(Optional.of(line));
        when(budgetLineRepository.findByBudgetId(100L)).thenReturn(List.of(line));
        authAs(approver);

        CustomResponse<?> r = service.approve(7L, null);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(am.getStatus()).isEqualTo(AmendmentStatus.APPROVED);
        assertThat(am.getFinalApprovedAmount()).isEqualByComparingTo("1200.00");
        assertThat(line.getAllocatedAmount()).isEqualByComparingTo("1200.00"); // current moved
        assertThat(line.getOriginalAllocatedAmount()).isEqualByComparingTo("1000.00"); // original preserved
        assertThat(b.getTotalExpenseAllocated()).isEqualByComparingTo("1200.00"); // totals recalculated
    }

    @Test
    @DisplayName("Approving an already-APPROVED amendment is rejected (cannot apply twice)")
    void cannotApplyTwice() {
        Budget b = approvedBudget();
        BudgetAmendment am = BudgetAmendment.builder()
                .id(7L).budget(b).type(AmendmentType.INCREASE).status(AmendmentStatus.APPROVED)
                .requestedBy(requester).requestedDelta(new BigDecimal("200.00"))
                .proposedAmount(new BigDecimal("1200.00")).build();
        when(amendmentRepository.findById(7L)).thenReturn(Optional.of(am));
        authAs(approver);

        CustomResponse<?> r = service.approve(7L, null);
        assertThat(r.getStatusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject requires a reason and records it")
    void rejectRequiresReason() {
        Budget b = approvedBudget();
        BudgetAmendment am = BudgetAmendment.builder()
                .id(7L).budget(b).type(AmendmentType.INCREASE).status(AmendmentStatus.PENDING)
                .requestedBy(requester).requestedDelta(new BigDecimal("200.00"))
                .proposedAmount(new BigDecimal("1200.00")).build();
        when(amendmentRepository.findById(7L)).thenReturn(Optional.of(am));
        authAs(approver);

        CustomResponse<?> noReason = service.reject(7L, new AmendmentDecisionRequest(null));
        assertThat(noReason.getStatusCode()).isEqualTo(400);

        CustomResponse<?> withReason = service.reject(7L, new AmendmentDecisionRequest("Not justified"));
        assertThat(withReason.getStatusCode()).isEqualTo(200);
        assertThat(am.getStatus()).isEqualTo(AmendmentStatus.REJECTED);
        assertThat(am.getRejectionReason()).isEqualTo("Not justified");
    }
}
