package com.EduePoa.EP.HR.payroll.service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.HR.compensation.entity.Allowance;
import com.EduePoa.EP.HR.compensation.entity.EmployeeCompensation;
import com.EduePoa.EP.HR.compensation.entity.RecurringDeduction;
import com.EduePoa.EP.HR.compensation.enums.AmountBasis;
import com.EduePoa.EP.HR.compensation.repository.EmployeeCompensationRepository;
import com.EduePoa.EP.HR.config.service.StatutoryConfigException;
import com.EduePoa.EP.HR.config.service.StatutoryConfigService;
import com.EduePoa.EP.HR.config.service.StatutoryConfigSnapshot;
import com.EduePoa.EP.HR.engine.PayrollCalculationService;
import com.EduePoa.EP.HR.engine.PayrollInput;
import com.EduePoa.EP.HR.engine.PayslipBreakdown;
import com.EduePoa.EP.HR.payroll.dto.request.ApproveRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.CreateRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.EmployeeAdjustmentRequest;
import com.EduePoa.EP.HR.payroll.dto.request.MarkPaidRequest;
import com.EduePoa.EP.HR.payroll.dto.response.*;
import com.EduePoa.EP.HR.payroll.entity.*;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.HR.payroll.enums.PayslipLineType;
import com.EduePoa.EP.HR.payroll.repository.PayrollEmployeeAdjustmentRepository;
import com.EduePoa.EP.HR.payroll.repository.PayrollRunRepository;
import com.EduePoa.EP.HR.payroll.repository.PayslipRepository;
import com.EduePoa.EP.HR.proration.service.ProrationPolicySnapshot;
import com.EduePoa.EP.HR.proration.service.ProrationResult;
import com.EduePoa.EP.HR.proration.service.ProrationService;
import com.EduePoa.EP.Staff.Staff;
import com.EduePoa.EP.Utils.CustomResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payroll run lifecycle: create -&gt; process -&gt; approve -&gt; mark paid, with segregation of
 * duties, concurrency guards and full audit. The deterministic figures come from the pure
 * {@link PayrollCalculationService}; this service resolves each employee's inputs, freezes the
 * statutory + proration snapshots on first processing, persists payslips, and reconciles totals.
 * <p>
 * Phase 1 explicitly does NOT post to the Finance/Expenses ledger — it preserves the run id,
 * period and reconciled totals that a future Phase 2 idempotent posting will consume.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollServiceImpl implements PayrollService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollEmployeeAdjustmentRepository adjustmentRepository;
    private final EmployeeCompensationRepository compensationRepository;
    private final StatutoryConfigService statutoryConfigService;
    private final ProrationService prorationService;
    private final PayrollCalculationService calculationService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PayslipPdfService payslipPdfService;
    private final com.EduePoa.EP.Multitenancy.repository.TenantRepository tenantRepository;

    // ---- Create -------------------------------------------------------------

    @Override
    @Audit(module = "PAYROLL", action = "CREATE_RUN")
    @Transactional
    public CustomResponse<?> createRun(CreateRunRequest request) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            if (runRepository.existsByPeriodYearAndPeriodMonth(request.getYear(), request.getMonth())) {
                return conflict(response, "A payroll run already exists for "
                        + request.getMonth() + "/" + request.getYear());
            }
            User actor = getCurrentUser();
            PayrollRun run = PayrollRun.builder()
                    .periodMonth(request.getMonth())
                    .periodYear(request.getYear())
                    .status(PayrollStatus.DRAFT)
                    .createdBy(actor)
                    .build();
            PayrollRun saved = runRepository.save(run);

            auditService.log("PAYROLL", "Created draft payroll run ID:", String.valueOf(saved.getId()),
                    "period:", request.getMonth() + "/" + request.getYear());

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Draft payroll run created for " + request.getMonth() + "/" + request.getYear());
            response.setEntity(toRunDto(saved, List.of()));
        } catch (RuntimeException e) {
            log.error("Error creating payroll run: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to create payroll run: " + e.getMessage());
        }
        return response;
    }

    // ---- Process ------------------------------------------------------------

    @Override
    @Audit(module = "PAYROLL", action = "PROCESS_RUN")
    @Transactional
    public CustomResponse<?> processRun(Long runId) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFound(response, "Payroll run not found with ID: " + runId);
            }
            // Only DRAFT or already-PROCESSED runs may be (re)processed; APPROVED/PAID are frozen.
            if (run.getStatus() == PayrollStatus.APPROVED || run.getStatus() == PayrollStatus.PAID) {
                return conflict(response, "Payroll run " + runId + " is " + run.getStatus()
                        + " and cannot be reprocessed");
            }

            YearMonth period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
            LocalDate asOf = period.atDay(1);

            // Resolve + freeze snapshots on first processing; reuse the frozen snapshots on reprocess
            // so a run's basis is stable across re-runs within the DRAFT/PROCESSED phase.
            StatutoryConfigSnapshot statutory;
            ProrationPolicySnapshot proration;
            try {
                statutory = statutoryConfigService.resolveFor(asOf);
            } catch (StatutoryConfigException e) {
                return badWithStatus(response, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Cannot process: " + e.getMessage());
            }
            proration = prorationService.resolveSnapshot(asOf);

            // Clear existing payslips (idempotent reprocess).
            payslipRepository.deleteByPayrollRunId(runId);
            payslipRepository.flush();

            List<EmployeeCompensation> comps = compensationRepository.findByActiveTrue();
            List<PayrollEmployeeAdjustment> adjustments = adjustmentRepository.findByPayrollRunId(runId);

            RunTotals totals = new RunTotals();
            List<String> warnings = new ArrayList<>();
            boolean blocking = false;
            int count = 0;

            for (EmployeeCompensation comp : comps) {
                Staff staff = comp.getStaff();
                if (staff == null || staff.getDeletedFlag() == 'Y') {
                    continue;
                }
                PayrollEmployeeAdjustment adj = adjustments.stream()
                        .filter(a -> a.getStaffId().equals(staff.getId()))
                        .findFirst().orElse(null);

                LocalDate joinDate = (adj != null && adj.getJoinDate() != null)
                        ? adj.getJoinDate() : staff.getDateOfEmployment();
                LocalDate leaveDate = adj != null ? adj.getLeaveDate() : null;
                int unpaidDays = adj != null ? adj.getUnpaidDays() : 0;

                ProrationResult prorationResult;
                try {
                    prorationResult = prorationService.prorate(proration, period, joinDate, leaveDate, unpaidDays);
                } catch (RuntimeException ex) {
                    // Invalid employment dates for this employee are a blocking validation error on
                    // the run, surfaced rather than silently skipped.
                    warnings.add("Staff " + staff.getId() + " (" + fullName(staff) + "): " + ex.getMessage());
                    blocking = true;
                    continue;
                }

                PayrollInput input = buildInput(comp, period, prorationResult);
                PayslipBreakdown b = calculationService.compute(input, statutory, proration);

                if (b.isBlocking()) {
                    blocking = true;
                }
                for (String w : b.getWarnings()) {
                    warnings.add("Staff " + staff.getId() + " (" + fullName(staff) + "): " + w);
                }

                Payslip slip = buildPayslip(run, staff, b, prorationResult);
                payslipRepository.save(slip);
                totals.add(b);
                count++;
            }

            // Apply computed totals to the run and persist.
            totals.applyTo(run);
            run.setEmployeeCount(count);
            run.setHasBlockingErrors(blocking);
            run.setStatutorySnapshotJson(writeJson(statutory));
            run.setProrationSnapshotJson(writeJson(proration));
            run.setProrationPolicyVersion(proration.getPolicyVersion());
            run.setStatus(PayrollStatus.PROCESSED);
            run.setProcessedBy(getCurrentUser());
            run.setProcessedAt(LocalDateTime.now());
            PayrollRun saved = runRepository.save(run);

            auditService.log("PAYROLL", "Processed run ID:", String.valueOf(runId),
                    "employees:", String.valueOf(count),
                    "totalNet:", String.valueOf(saved.getTotalNet()),
                    "blocking:", String.valueOf(blocking));

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(blocking
                    ? "Run processed with blocking validation errors; resolve them before approval"
                    : "Run processed successfully (" + count + " payslips)");
            response.setEntity(toRunDto(saved, warnings));
        } catch (RuntimeException e) {
            log.error("Error processing payroll run {}: {}", runId, e.getMessage(), e);
            throw new RuntimeException("Failed to process payroll run: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Approve (checker; must differ from processor) ----------------------

    @Override
    @Audit(module = "PAYROLL", action = "APPROVE_RUN")
    @Transactional
    public CustomResponse<?> approveRun(Long runId, ApproveRunRequest request) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFound(response, "Payroll run not found with ID: " + runId);
            }
            if (run.getStatus() != PayrollStatus.PROCESSED) {
                return conflict(response, "Only a PROCESSED run can be approved (current status: "
                        + run.getStatus() + ")");
            }
            if (run.isHasBlockingErrors()) {
                return conflict(response,
                        "Run " + runId + " has unresolved blocking validation errors and cannot be approved");
            }

            User checker = getCurrentUser();
            // Segregation of duties: the processor must not approve their own run. Validated against
            // the authenticated actor, never a client-supplied id.
            if (run.getProcessedBy() != null && run.getProcessedBy().getId() != null
                    && run.getProcessedBy().getId().equals(checker.getId())) {
                return conflict(response,
                        "The user who processed this run cannot approve it. A different approver is required.");
            }

            run.setStatus(PayrollStatus.APPROVED);
            run.setApprovedBy(checker);
            run.setApprovedAt(LocalDateTime.now());
            run.setApprovalComment(request != null ? request.getComment() : null);
            PayrollRun saved = runRepository.save(run);

            auditService.log("PAYROLL", "Approved run ID:", String.valueOf(runId),
                    "approvedBy:", checker.getUsername());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payroll run approved and frozen");
            response.setEntity(toRunDto(saved, List.of()));
        } catch (RuntimeException e) {
            log.error("Error approving payroll run {}: {}", runId, e.getMessage(), e);
            throw new RuntimeException("Failed to approve payroll run: " + e.getMessage(), e);
        }
        return response;
    }

    @Override
    @Audit(module = "PAYROLL", action = "REJECT_RUN")
    @Transactional
    public CustomResponse<?> rejectRun(Long runId, String reason) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFound(response, "Payroll run not found with ID: " + runId);
            }
            if (run.getStatus() != PayrollStatus.PROCESSED) {
                return conflict(response, "Only a PROCESSED run can be rejected (current status: "
                        + run.getStatus() + ")");
            }
            run.setStatus(PayrollStatus.DRAFT);
            run.setApprovalComment(reason);
            run.setApprovedBy(null);
            run.setApprovedAt(null);
            PayrollRun saved = runRepository.save(run);

            auditService.log("PAYROLL", "Rejected run ID:", String.valueOf(runId),
                    "reason:", reason != null ? reason : "");

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payroll run returned to DRAFT");
            response.setEntity(toRunDto(saved, List.of()));
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to reject payroll run: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Mark paid ----------------------------------------------------------

    @Override
    @Audit(module = "PAYROLL", action = "MARK_PAID")
    @Transactional
    public CustomResponse<?> markPaid(Long runId, MarkPaidRequest request) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFound(response, "Payroll run not found with ID: " + runId);
            }
            if (run.getStatus() != PayrollStatus.APPROVED) {
                return conflict(response, "Only an APPROVED run can be marked paid (current status: "
                        + run.getStatus() + ")");
            }
            run.setStatus(PayrollStatus.PAID);
            run.setPaidBy(getCurrentUser());
            run.setPaidAt(LocalDateTime.now());
            run.setPaymentReference(request != null ? request.getPaymentReference() : null);
            PayrollRun saved = runRepository.save(run);

            // Phase 1: payment status is recorded WITHOUT posting to the Finance/Expenses ledger.
            auditService.log("PAYROLL", "Marked run PAID ID:", String.valueOf(runId),
                    "reference:", saved.getPaymentReference() != null ? saved.getPaymentReference() : "");

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payroll run marked as paid (no Finance posting in Phase 1)");
            response.setEntity(toRunDto(saved, List.of()));
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to mark payroll run paid: " + e.getMessage(), e);
        }
        return response;
    }

    // ---- Employee adjustments (joiners/leavers/unpaid) ----------------------

    @Override
    @Transactional
    public CustomResponse<?> setEmployeeAdjustment(Long runId, EmployeeAdjustmentRequest request) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFoundObj(response, "Payroll run not found with ID: " + runId);
            }
            if (run.getStatus() == PayrollStatus.APPROVED || run.getStatus() == PayrollStatus.PAID) {
                return conflictObj(response, "Run " + runId + " is " + run.getStatus()
                        + "; employment adjustments can only change a DRAFT/PROCESSED run");
            }
            PayrollEmployeeAdjustment adj = adjustmentRepository
                    .findByPayrollRunIdAndStaffId(runId, request.getStaffId())
                    .orElseGet(PayrollEmployeeAdjustment::new);
            adj.setPayrollRunId(runId);
            adj.setStaffId(request.getStaffId());
            adj.setJoinDate(request.getJoinDate());
            adj.setLeaveDate(request.getLeaveDate());
            adj.setUnpaidDays(Math.max(0, request.getUnpaidDays()));
            adjustmentRepository.save(adj);

            auditService.log("PAYROLL", "Set employment adjustment on run:", String.valueOf(runId),
                    "staff:", String.valueOf(request.getStaffId()));

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Employment adjustment saved. Reprocess the run to apply it.");
            response.setEntity(Map.of("runId", runId, "staffId", request.getStaffId()));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to save employment adjustment: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> listEmployeeAdjustments(Long runId) {
        CustomResponse<List<PayrollEmployeeAdjustment>> response = new CustomResponse<>();
        try {
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Adjustments retrieved");
            response.setEntity(adjustmentRepository.findByPayrollRunId(runId));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to list adjustments: " + e.getMessage());
        }
        return response;
    }

    // ---- Reads --------------------------------------------------------------

    @Override
    public CustomResponse<?> listRuns(PayrollStatus status, int page, int size) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 10 : size);
            Page<PayrollRun> p = (status == null)
                    ? runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc(pageable)
                    : runRepository.findByStatusOrderByPeriodYearDescPeriodMonthDesc(status, pageable);
            List<PayrollRunResponse> content = p.getContent().stream()
                    .map(r -> toRunDto(r, List.of())).toList();
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(content.isEmpty() ? "No payroll runs found" : "Payroll runs retrieved");
            response.setEntity(Map.of(
                    "content", content,
                    "currentPage", p.getNumber(),
                    "totalItems", p.getTotalElements(),
                    "totalPages", p.getTotalPages()));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to list payroll runs: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getRun(Long runId) {
        CustomResponse<PayrollRunResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFound(response, "Payroll run not found with ID: " + runId);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payroll run retrieved");
            response.setEntity(toRunDto(run, List.of()));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve payroll run: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getRunPayslips(Long runId) {
        CustomResponse<List<PayslipResponse>> response = new CustomResponse<>();
        try {
            if (runRepository.findById(runId).isEmpty()) {
                return notFoundList(response, "Payroll run not found with ID: " + runId);
            }
            List<PayslipResponse> slips = payslipRepository.findByPayrollRunId(runId).stream()
                    .map(this::toPayslipDto).toList();
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(slips.isEmpty() ? "No payslips found for this run" : "Payslips retrieved");
            response.setEntity(slips);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve payslips: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getPayslip(Long payslipId) {
        CustomResponse<PayslipResponse> response = new CustomResponse<>();
        try {
            Payslip slip = payslipRepository.findById(payslipId).orElse(null);
            if (slip == null) {
                return notFoundSlip(response, "Payslip not found with ID: " + payslipId);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Payslip retrieved");
            response.setEntity(toPayslipDto(slip));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve payslip: " + e.getMessage());
        }
        return response;
    }

    @Override
    public byte[] getPayslipPdf(Long payslipId) {
        Payslip slip = payslipRepository.findById(payslipId).orElse(null);
        if (slip == null) {
            return null;
        }
        PayslipResponse dto = toPayslipDto(slip);
        return payslipPdfService.render(dto, resolveSchoolName(slip.getTenantId()));
    }

    @Override
    public String getPayslipPdfFileName(Long payslipId) {
        Payslip slip = payslipRepository.findById(payslipId).orElse(null);
        if (slip == null) {
            return "payslip.pdf";
        }
        return payslipPdfService.suggestedFileName(toPayslipDto(slip));
    }

    /** Resolves the tenant's school name for the PDF header; null-safe. */
    private String resolveSchoolName(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantRepository.findByTenantIdentifier(tenantId)
                .map(com.EduePoa.EP.Multitenancy.entity.Tenant::getSchoolName)
                .orElse(null);
    }

    @Override
    public CustomResponse<?> getStatutorySummary(Long runId) {
        CustomResponse<StatutorySummaryResponse> response = new CustomResponse<>();
        try {
            PayrollRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                return notFoundSummary(response, "Payroll run not found with ID: " + runId);
            }
            // Reconcile the stored run totals against a fresh sum of the run's payslips.
            List<Payslip> slips = payslipRepository.findByPayrollRunId(runId);
            RunTotals fromSlips = new RunTotals();
            for (Payslip s : slips) {
                fromSlips.addPayslip(s);
            }
            boolean reconciled = fromSlips.reconcilesWith(run);
            String note = reconciled
                    ? "Payslip totals reconcile with the stored run totals"
                    : "MISMATCH between payslip sums and stored run totals — investigate before remittance";

            StatutorySummaryResponse summary = StatutorySummaryResponse.builder()
                    .payrollRunId(run.getId())
                    .periodMonth(run.getPeriodMonth())
                    .periodYear(run.getPeriodYear())
                    .employeeCount(run.getEmployeeCount())
                    .paye(run.getTotalPaye())
                    .nssfEmployee(run.getTotalNssfEmployee())
                    .shif(run.getTotalShif())
                    .ahlEmployee(run.getTotalAhlEmployee())
                    .otherDeductions(run.getTotalOtherDeductions())
                    .totalEmployeeDeductions(run.getTotalEmployeeDeductions())
                    .nssfEmployer(run.getTotalNssfEmployer())
                    .ahlEmployer(run.getTotalAhlEmployer())
                    .nita(run.getTotalNita())
                    .totalEmployerContributions(run.getTotalEmployerContributions())
                    .nssfTotalRemittance(nz(run.getTotalNssfEmployee()).add(nz(run.getTotalNssfEmployer())))
                    .ahlTotalRemittance(nz(run.getTotalAhlEmployee()).add(nz(run.getTotalAhlEmployer())))
                    .totalGross(run.getTotalGross())
                    .totalNet(run.getTotalNet())
                    .totalEmployerCost(run.getTotalEmployerCost())
                    .reconciled(reconciled)
                    .reconciliationNote(note)
                    .build();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Statutory summary retrieved");
            response.setEntity(summary);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve statutory summary: " + e.getMessage());
        }
        return response;
    }

    // ---- Input resolution ---------------------------------------------------

    private PayrollInput buildInput(EmployeeCompensation comp, YearMonth period, ProrationResult proration) {
        BigDecimal basic = nz(comp.getBasicSalary());
        List<PayrollInput.EarningLine> allowances = new ArrayList<>();
        for (Allowance a : comp.getAllowances()) {
            BigDecimal amount = evaluate(a.getBasis(), a.getAmount(), basic);
            allowances.add(PayrollInput.EarningLine.builder()
                    .name(a.getName())
                    .amount(amount)
                    .taxable(a.isTaxable())
                    .pensionable(a.isPensionable())
                    .fixedMonthly(a.isFixedMonthly())
                    .build());
        }
        List<PayrollInput.DeductionLine> deductions = new ArrayList<>();
        LocalDate periodEnd = period.atEndOfMonth();
        for (RecurringDeduction d : comp.getRecurringDeductions()) {
            if (!d.isActiveOn(periodEnd)) {
                continue;
            }
            BigDecimal amount = evaluate(d.getBasis(), d.getAmount(), basic);
            deductions.add(PayrollInput.DeductionLine.builder()
                    .name(d.getName())
                    .amount(amount)
                    .preTax(d.isPreTax())
                    .build());
        }
        return PayrollInput.builder()
                .basicSalary(basic)
                .allowances(allowances)
                .otherDeductions(deductions)
                .insurancePremiumMonthly(nz(comp.getInsurancePremiumMonthly()))
                .nssfPensionablePayOverride(comp.getNssfPensionablePayOverride())
                .proration(proration)
                .build();
    }

    /** FIXED => the amount as-is; PERCENTAGE_OF_BASIC => fraction * basic. */
    private BigDecimal evaluate(AmountBasis basis, BigDecimal amount, BigDecimal basic) {
        BigDecimal amt = nz(amount);
        if (basis == AmountBasis.PERCENTAGE_OF_BASIC) {
            return basic.multiply(amt);
        }
        return amt;
    }

    private Payslip buildPayslip(PayrollRun run, Staff staff, PayslipBreakdown b, ProrationResult pr) {
        Payslip slip = Payslip.builder()
                .payrollRun(run)
                .staff(staff)
                .staffName(fullName(staff))
                .employeeNumber(staff.getEmployeeNumber())
                .basicPay(b.getProratedBasic())
                .grossPay(b.getGrossPay())
                .paye(b.getPayePayable())
                .nssfEmployee(b.getNssfEmployee())
                .shif(b.getShif())
                .ahlEmployee(b.getAhlEmployee())
                .otherDeductions(nz(b.getOtherPreTaxDeductions()).add(nz(b.getOtherPostTaxDeductions())))
                .taxableIncome(b.getTaxableIncome())
                .personalRelief(b.getPersonalRelief())
                .insuranceRelief(b.getInsuranceRelief())
                .totalEmployeeDeductions(b.getTotalEmployeeDeductions())
                .netPay(b.getNetPay())
                .nssfEmployer(b.getNssfEmployer())
                .ahlEmployer(b.getAhlEmployer())
                .nita(b.getNita())
                .totalEmployerContributions(b.getTotalEmployerContributions())
                .prorated(pr != null && !pr.isFullPeriod())
                .eligibleDays(pr != null ? pr.getEligibleDays() : null)
                .totalDays(pr != null ? pr.getTotalDays() : null)
                .build();

        int order = 0;
        for (PayslipBreakdown.Line l : b.getLines()) {
            slip.getLines().add(PayslipLine.builder()
                    .payslip(slip)
                    .lineType(PayslipLineType.valueOf(l.getLineType().name()))
                    .name(l.getName())
                    .amount(l.getAmount())
                    .sortOrder(order++)
                    .build());
        }
        return slip;
    }

    // ---- Totals accumulator -------------------------------------------------

    /** Accumulates run-level totals from engine breakdowns and reconciles against stored runs. */
    private static final class RunTotals {
        BigDecimal gross = ZERO, paye = ZERO, nssfEe = ZERO, nssfEr = ZERO, shif = ZERO,
                ahlEe = ZERO, ahlEr = ZERO, nita = ZERO, other = ZERO, empDed = ZERO,
                empContrib = ZERO, net = ZERO, empCost = ZERO;

        void add(PayslipBreakdown b) {
            gross = gross.add(nz(b.getGrossPay()));
            paye = paye.add(nz(b.getPayePayable()));
            nssfEe = nssfEe.add(nz(b.getNssfEmployee()));
            nssfEr = nssfEr.add(nz(b.getNssfEmployer()));
            shif = shif.add(nz(b.getShif()));
            ahlEe = ahlEe.add(nz(b.getAhlEmployee()));
            ahlEr = ahlEr.add(nz(b.getAhlEmployer()));
            nita = nita.add(nz(b.getNita()));
            other = other.add(nz(b.getOtherPreTaxDeductions())).add(nz(b.getOtherPostTaxDeductions()));
            empDed = empDed.add(nz(b.getTotalEmployeeDeductions()));
            empContrib = empContrib.add(nz(b.getTotalEmployerContributions()));
            net = net.add(nz(b.getNetPay()));
            empCost = empCost.add(nz(b.getTotalEmployerCost()));
        }

        void addPayslip(Payslip s) {
            gross = gross.add(nz(s.getGrossPay()));
            paye = paye.add(nz(s.getPaye()));
            nssfEe = nssfEe.add(nz(s.getNssfEmployee()));
            nssfEr = nssfEr.add(nz(s.getNssfEmployer()));
            shif = shif.add(nz(s.getShif()));
            ahlEe = ahlEe.add(nz(s.getAhlEmployee()));
            ahlEr = ahlEr.add(nz(s.getAhlEmployer()));
            nita = nita.add(nz(s.getNita()));
            other = other.add(nz(s.getOtherDeductions()));
            empDed = empDed.add(nz(s.getTotalEmployeeDeductions()));
            empContrib = empContrib.add(nz(s.getTotalEmployerContributions()));
            net = net.add(nz(s.getNetPay()));
        }

        void applyTo(PayrollRun run) {
            run.setTotalGross(scale(gross));
            run.setTotalPaye(scale(paye));
            run.setTotalNssfEmployee(scale(nssfEe));
            run.setTotalNssfEmployer(scale(nssfEr));
            run.setTotalShif(scale(shif));
            run.setTotalAhlEmployee(scale(ahlEe));
            run.setTotalAhlEmployer(scale(ahlEr));
            run.setTotalNita(scale(nita));
            run.setTotalOtherDeductions(scale(other));
            run.setTotalEmployeeDeductions(scale(empDed));
            run.setTotalEmployerContributions(scale(empContrib));
            run.setTotalNet(scale(net));
            run.setTotalEmployerCost(scale(empCost));
        }

        boolean reconcilesWith(PayrollRun run) {
            return eq(scale(gross), run.getTotalGross())
                    && eq(scale(paye), run.getTotalPaye())
                    && eq(scale(nssfEe), run.getTotalNssfEmployee())
                    && eq(scale(shif), run.getTotalShif())
                    && eq(scale(ahlEe), run.getTotalAhlEmployee())
                    && eq(scale(empDed), run.getTotalEmployeeDeductions())
                    && eq(scale(empContrib), run.getTotalEmployerContributions())
                    && eq(scale(net), run.getTotalNet());
        }

        private static boolean eq(BigDecimal a, BigDecimal b) {
            return nz(a).compareTo(nz(b)) == 0;
        }

        private static BigDecimal scale(BigDecimal v) {
            return nz(v).setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ---- DTO mapping --------------------------------------------------------

    private PayrollRunResponse toRunDto(PayrollRun r, List<String> warnings) {
        return PayrollRunResponse.builder()
                .id(r.getId())
                .periodMonth(r.getPeriodMonth())
                .periodYear(r.getPeriodYear())
                .status(r.getStatus())
                .totalGross(r.getTotalGross())
                .totalPaye(r.getTotalPaye())
                .totalNssfEmployee(r.getTotalNssfEmployee())
                .totalNssfEmployer(r.getTotalNssfEmployer())
                .totalShif(r.getTotalShif())
                .totalAhlEmployee(r.getTotalAhlEmployee())
                .totalAhlEmployer(r.getTotalAhlEmployer())
                .totalNita(r.getTotalNita())
                .totalOtherDeductions(r.getTotalOtherDeductions())
                .totalEmployeeDeductions(r.getTotalEmployeeDeductions())
                .totalEmployerContributions(r.getTotalEmployerContributions())
                .totalNet(r.getTotalNet())
                .totalEmployerCost(r.getTotalEmployerCost())
                .employeeCount(r.getEmployeeCount())
                .hasBlockingErrors(r.isHasBlockingErrors())
                .prorationPolicyVersion(r.getProrationPolicyVersion())
                .processedByName(r.getProcessedBy() != null ? r.getProcessedBy().getUsername() : null)
                .processedAt(r.getProcessedAt())
                .approvedByName(r.getApprovedBy() != null ? r.getApprovedBy().getUsername() : null)
                .approvedAt(r.getApprovedAt())
                .approvalComment(r.getApprovalComment())
                .paidAt(r.getPaidAt())
                .paymentReference(r.getPaymentReference())
                .createdAt(r.getCreatedAt())
                .warnings(warnings)
                .build();
    }

    private PayslipResponse toPayslipDto(Payslip s) {
        PayrollRun run = s.getPayrollRun();
        List<PayslipLineResponse> lines = s.getLines().stream()
                .sorted((a, b) -> Integer.compare(
                        a.getSortOrder() != null ? a.getSortOrder() : 0,
                        b.getSortOrder() != null ? b.getSortOrder() : 0))
                .map(l -> PayslipLineResponse.builder()
                        .lineType(l.getLineType())
                        .name(l.getName())
                        .amount(l.getAmount())
                        .build())
                .toList();
        return PayslipResponse.builder()
                .id(s.getId())
                .payrollRunId(run != null ? run.getId() : null)
                .periodMonth(run != null ? run.getPeriodMonth() : null)
                .periodYear(run != null ? run.getPeriodYear() : null)
                .staffId(s.getStaff() != null ? s.getStaff().getId() : null)
                .staffName(s.getStaffName())
                .employeeNumber(s.getEmployeeNumber())
                .basicPay(s.getBasicPay())
                .grossPay(s.getGrossPay())
                .paye(s.getPaye())
                .nssfEmployee(s.getNssfEmployee())
                .shif(s.getShif())
                .ahlEmployee(s.getAhlEmployee())
                .otherDeductions(s.getOtherDeductions())
                .taxableIncome(s.getTaxableIncome())
                .personalRelief(s.getPersonalRelief())
                .insuranceRelief(s.getInsuranceRelief())
                .totalEmployeeDeductions(s.getTotalEmployeeDeductions())
                .netPay(s.getNetPay())
                .nssfEmployer(s.getNssfEmployer())
                .ahlEmployer(s.getAhlEmployer())
                .nita(s.getNita())
                .totalEmployerContributions(s.getTotalEmployerContributions())
                .prorated(s.isProrated())
                .eligibleDays(s.getEligibleDays())
                .totalDays(s.getTotalDays())
                .lines(lines)
                .build();
    }

    // ---- infra helpers ------------------------------------------------------

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize snapshot: " + e.getMessage(), e);
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private static String fullName(Staff s) {
        StringBuilder sb = new StringBuilder();
        if (s.getFirstName() != null) sb.append(s.getFirstName().trim());
        if (s.getOtherNames() != null && !s.getOtherNames().isBlank()) sb.append(" ").append(s.getOtherNames().trim());
        if (s.getLastName() != null) sb.append(" ").append(s.getLastName().trim());
        return sb.toString().trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    // ---- response helpers ---------------------------------------------------

    private CustomResponse<PayrollRunResponse> notFound(CustomResponse<PayrollRunResponse> r, String msg) {
        return set(r, HttpStatus.NOT_FOUND, msg);
    }

    private CustomResponse<PayrollRunResponse> conflict(CustomResponse<PayrollRunResponse> r, String msg) {
        return set(r, HttpStatus.CONFLICT, msg);
    }

    private CustomResponse<PayrollRunResponse> badWithStatus(CustomResponse<PayrollRunResponse> r,
                                                             HttpStatus status, String msg) {
        return set(r, status, msg);
    }

    private CustomResponse<PayrollRunResponse> set(CustomResponse<PayrollRunResponse> r,
                                                   HttpStatus status, String msg) {
        r.setStatusCode(status.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<List<PayslipResponse>> notFoundList(CustomResponse<List<PayslipResponse>> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<PayslipResponse> notFoundSlip(CustomResponse<PayslipResponse> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<StatutorySummaryResponse> notFoundSummary(CustomResponse<StatutorySummaryResponse> r,
                                                                     String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<Object> notFoundObj(CustomResponse<Object> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<Object> conflictObj(CustomResponse<Object> r, String msg) {
        r.setStatusCode(HttpStatus.CONFLICT.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
