package com.EduePoa.EP.HR.payroll.controller;

import com.EduePoa.EP.HR.payroll.dto.request.ApproveRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.CreateRunRequest;
import com.EduePoa.EP.HR.payroll.dto.request.EmployeeAdjustmentRequest;
import com.EduePoa.EP.HR.payroll.dto.request.MarkPaidRequest;
import com.EduePoa.EP.HR.payroll.enums.PayrollStatus;
import com.EduePoa.EP.HR.payroll.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Payroll run workflow. Permissions are segregated per the confirmed decisions:
 * <ul>
 *   <li>{@code payroll:process} — create, process/reprocess, set employment adjustments, mark paid.</li>
 *   <li>{@code payroll:approve} — approve or reject a processed run (must be a different user than
 *       the processor; enforced in the service against the authenticated actor).</li>
 *   <li>{@code payroll:read} — all read endpoints (run list/detail, payslips, statutory summary).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/hr/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    // ---- Processor actions ----

    @PostMapping("/runs")
    @PreAuthorize("hasPermission(null, 'payroll:process')")
    public ResponseEntity<?> createRun(@Valid @RequestBody CreateRunRequest request) {
        var r = payrollService.createRun(request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PostMapping("/runs/{id}/process")
    @PreAuthorize("hasPermission(null, 'payroll:process')")
    public ResponseEntity<?> process(@PathVariable Long id) {
        var r = payrollService.processRun(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PostMapping("/runs/{id}/adjustments")
    @PreAuthorize("hasPermission(null, 'payroll:process')")
    public ResponseEntity<?> setAdjustment(@PathVariable Long id,
                                           @Valid @RequestBody EmployeeAdjustmentRequest request) {
        var r = payrollService.setEmployeeAdjustment(id, request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @GetMapping("/runs/{id}/adjustments")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process')")
    public ResponseEntity<?> listAdjustments(@PathVariable Long id) {
        var r = payrollService.listEmployeeAdjustments(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PostMapping("/runs/{id}/mark-paid")
    @PreAuthorize("hasPermission(null, 'payroll:process')")
    public ResponseEntity<?> markPaid(@PathVariable Long id,
                                      @RequestBody(required = false) MarkPaidRequest request) {
        var r = payrollService.markPaid(id, request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    // ---- Approver actions ----

    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestBody(required = false) ApproveRunRequest request) {
        var r = payrollService.approveRun(id, request);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @PostMapping("/runs/{id}/reject")
    @PreAuthorize("hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestParam(required = false) String reason) {
        var r = payrollService.rejectRun(id, reason);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    // ---- Reads ----

    @GetMapping("/runs")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> listRuns(@RequestParam(required = false) PayrollStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "12") int size) {
        var r = payrollService.listRuns(status, page, size);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> getRun(@PathVariable Long id) {
        var r = payrollService.getRun(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @GetMapping("/runs/{id}/payslips")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> getRunPayslips(@PathVariable Long id) {
        var r = payrollService.getRunPayslips(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    @GetMapping("/payslips/{id}")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> getPayslip(@PathVariable Long id) {
        var r = payrollService.getPayslip(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }

    /**
     * Downloads a single payslip as a backend-generated PDF. Same read permissions as the JSON
     * payslip endpoint. Returns 404 when the payslip does not exist.
     */
    @GetMapping(value = "/payslips/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<byte[]> getPayslipPdf(@PathVariable Long id) {
        byte[] pdf = payrollService.getPayslipPdf(id);
        if (pdf == null) {
            return ResponseEntity.notFound().build();
        }
        String fileName = payrollService.getPayslipPdfFileName(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/runs/{id}/statutory-summary")
    @PreAuthorize("hasPermission(null, 'payroll:read') or hasPermission(null, 'payroll:process') "
            + "or hasPermission(null, 'payroll:approve')")
    public ResponseEntity<?> getStatutorySummary(@PathVariable Long id) {
        var r = payrollService.getStatutorySummary(id);
        return ResponseEntity.status(r.getStatusCode()).body(r);
    }
}
