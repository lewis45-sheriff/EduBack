package com.EduePoa.EP.Budget.Controller;

import com.EduePoa.EP.Budget.DTO.Request.AmendmentDecisionRequest;
import com.EduePoa.EP.Budget.DTO.Request.AmendmentSubmitRequest;
import com.EduePoa.EP.Budget.Service.BudgetAmendmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlled budget amendment workflow. Submission/approval/rejection require {@code budget:amend};
 * approval additionally enforces segregation of duties in the service layer. Reads (history/detail)
 * require {@code budget_report:read}.
 */
@RestController
@RequestMapping("/api/v1/budget-amendments")
@RequiredArgsConstructor
public class BudgetAmendmentController {

    private final BudgetAmendmentService amendmentService;

    @PostMapping("/budget/{budgetId}")
    @PreAuthorize("hasPermission(null, 'budget:amend')")
    public ResponseEntity<?> submit(@PathVariable Long budgetId,
                                    @Valid @RequestBody AmendmentSubmitRequest request) {
        var response = amendmentService.submit(budgetId, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'budget:amend')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestBody(required = false) AmendmentDecisionRequest request) {
        var response = amendmentService.approve(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission(null, 'budget:amend')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody AmendmentDecisionRequest request) {
        var response = amendmentService.reject(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = amendmentService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/budget/{budgetId}")
    @PreAuthorize("hasPermission(null, 'budget_report:read')")
    public ResponseEntity<?> history(@PathVariable Long budgetId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var response = amendmentService.history(budgetId, page, size);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
