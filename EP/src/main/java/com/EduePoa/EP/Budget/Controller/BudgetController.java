package com.EduePoa.EP.Budget.Controller;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Budget.DTO.Request.BudgetApprovalRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetClosureRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetLineRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetUpdateRequest;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import com.EduePoa.EP.Budget.Service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Year;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasPermission(null, 'budget:create')")
    public ResponseEntity<?> create(@Valid @RequestBody BudgetCreateRequest request) {
        var response = budgetService.create(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/get-all"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) BudgetStatus status,
            @RequestParam(required = false) Year academicYear,
            @RequestParam(required = false) Term academicTerm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {
        var response = budgetService.getAll(page, size, sortBy, sortDir, status,
                academicYear, academicTerm, startDate, endDate, search);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"/{id}", "/get-by-id/{id}"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = budgetService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping({"/{id}", "/update/{id}"})
    @PreAuthorize("hasPermission(null, 'budget:create')")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody BudgetUpdateRequest request) {
        var response = budgetService.update(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping({"/{id}", "/delete/{id}"})
    @PreAuthorize("hasPermission(null, 'budget:manage')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = budgetService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ---- Line management (DRAFT only) ----

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasPermission(null, 'budget:create')")
    public ResponseEntity<?> addLine(@PathVariable Long id, @Valid @RequestBody BudgetLineRequest request) {
        var response = budgetService.addLine(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasPermission(null, 'budget:create')")
    public ResponseEntity<?> updateLine(@PathVariable Long id, @PathVariable Long lineId,
                                        @Valid @RequestBody BudgetLineRequest request) {
        var response = budgetService.updateLine(id, lineId, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasPermission(null, 'budget:manage')")
    public ResponseEntity<?> removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        var response = budgetService.removeLine(id, lineId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // ---- Lifecycle ----

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'budget:approve')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestBody(required = false) BudgetApprovalRequest request) {
        var response = budgetService.approve(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'budget:close')")
    public ResponseEntity<?> close(@PathVariable Long id,
                                   @RequestBody(required = false) BudgetClosureRequest request) {
        var response = budgetService.close(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
