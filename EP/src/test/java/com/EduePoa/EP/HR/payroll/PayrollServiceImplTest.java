package com.EduePoa.EP.HR.payroll;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.HR.compensation.repository.EmployeeCompensationRepository;
import com.EduePoa.EP.HR.config.service.StatutoryConfigService;
import com.EduePoa.EP.HR.engine.PayrollCalculationService;
import com.EduePoa.EP.HR.payroll.dto.request.ApproveRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.CreateRunRequest;
import com.EduePoa.EP.HR.payroll.entity.PayrollRun;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.HR.payroll.repository.PayrollEmployeeAdjustmentRepository;
import com.EduePoa.EP.HR.payroll.repository.PayrollRunRepository;
import com.EduePoa.EP.HR.payroll.repository.PayslipRepository;
import com.EduePoa.EP.HR.payroll.service.PayrollServiceImpl;
import com.EduePoa.EP.HR.proration.service.ProrationService;
import com.EduePoa.EP.Utils.CustomResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the payroll workflow: segregation of duties (processor cannot approve), status
 * transition guards (only PROCESSED can be approved; APPROVED/PAID are frozen), the blocking-error
 * gate on approval, and duplicate-run prevention. These isolate the service with mocked
 * collaborators; the deterministic figures are covered by the engine tests.
 */
@ExtendWith(MockitoExtension.class)
class PayrollServiceImplTest {

    @Mock private PayrollRunRepository runRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayrollEmployeeAdjustmentRepository adjustmentRepository;
    @Mock private EmployeeCompensationRepository compensationRepository;
    @Mock private StatutoryConfigService statutoryConfigService;
    @Mock private ProrationService prorationService;
    @Mock private PayrollCalculationService calculationService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PayrollServiceImpl service;

    private static final String PROCESSOR_EMAIL = "processor@school.test";
    private static final String APPROVER_EMAIL = "approver@school.test";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUsername(email);
        return u;
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "x"));
    }

    @Test
    @DisplayName("Creating a run for an existing month/year is rejected as a conflict")
    void duplicateRunRejected() {
        when(runRepository.existsByPeriodYearAndPeriodMonth(2025, 3)).thenReturn(true);
        CreateRunRequest req = new CreateRunRequest();
        req.setMonth(3);
        req.setYear(2025);

        CustomResponse<?> resp = service.createRun(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("Segregation of duties: the processor cannot approve the run they processed")
    void processorCannotApprove() {
        User processor = user(1L, PROCESSOR_EMAIL);
        PayrollRun run = PayrollRun.builder()
                .id(10L).periodMonth(3).periodYear(2025)
                .status(PayrollStatus.PROCESSED)
                .hasBlockingErrors(false)
                .processedBy(processor)
                .build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        when(userRepository.findByEmail(PROCESSOR_EMAIL)).thenReturn(Optional.of(processor));
        authenticateAs(PROCESSOR_EMAIL);

        CustomResponse<?> resp = service.approveRun(10L, new ApproveRunRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(resp.getMessage()).contains("cannot approve");
        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("A different user can approve a processed run")
    void differentUserCanApprove() {
        User processor = user(1L, PROCESSOR_EMAIL);
        User approver = user(2L, APPROVER_EMAIL);
        PayrollRun run = PayrollRun.builder()
                .id(10L).periodMonth(3).periodYear(2025)
                .status(PayrollStatus.PROCESSED)
                .hasBlockingErrors(false)
                .processedBy(processor)
                .build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        when(userRepository.findByEmail(APPROVER_EMAIL)).thenReturn(Optional.of(approver));
        when(runRepository.save(any(PayrollRun.class))).thenAnswer(inv -> inv.getArgument(0));
        authenticateAs(APPROVER_EMAIL);

        CustomResponse<?> resp = service.approveRun(10L, new ApproveRunRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.APPROVED);
        assertThat(run.getApprovedBy()).isEqualTo(approver);
    }

    @Test
    @DisplayName("Approval requires PROCESSED status; a DRAFT run cannot be approved")
    void cannotApproveDraft() {
        PayrollRun run = PayrollRun.builder()
                .id(10L).status(PayrollStatus.DRAFT).build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        CustomResponse<?> resp = service.approveRun(10L, new ApproveRunRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("A run with unresolved blocking errors cannot be approved")
    void cannotApproveWithBlockingErrors() {
        User approver = user(2L, APPROVER_EMAIL);
        PayrollRun run = PayrollRun.builder()
                .id(10L).status(PayrollStatus.PROCESSED)
                .hasBlockingErrors(true)
                .processedBy(user(1L, PROCESSOR_EMAIL))
                .build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        CustomResponse<?> resp = service.approveRun(10L, new ApproveRunRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(resp.getMessage()).contains("blocking");
        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("An APPROVED run is frozen and cannot be reprocessed")
    void cannotReprocessApproved() {
        PayrollRun run = PayrollRun.builder()
                .id(10L).status(PayrollStatus.APPROVED).periodMonth(3).periodYear(2025).build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        CustomResponse<?> resp = service.processRun(10L);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        verify(payslipRepository, never()).deleteByPayrollRunId(anyLong());
    }

    @Test
    @DisplayName("Only an APPROVED run may be marked paid")
    void cannotMarkPaidUnlessApproved() {
        PayrollRun run = PayrollRun.builder()
                .id(10L).status(PayrollStatus.PROCESSED).build();
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        CustomResponse<?> resp = service.markPaid(10L, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("Approving a non-existent run returns not found")
    void approveMissingRun() {
        when(runRepository.findById(99L)).thenReturn(Optional.empty());
        CustomResponse<?> resp = service.approveRun(99L, new ApproveRunRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
