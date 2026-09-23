package com.EduePoa.EP.Budget.Controller;

import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryUpdateRequest;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Service.BudgetCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budget-categories")
@RequiredArgsConstructor
public class BudgetCategoryController {

    private final BudgetCategoryService budgetCategoryService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasPermission(null, 'budget_category:manage')")
    public ResponseEntity<?> create(@Valid @RequestBody BudgetCategoryCreateRequest request) {
        var response = budgetCategoryService.create(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/get-all"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) CategoryStatus status,
            @RequestParam(required = false) String search) {
        var response = budgetCategoryService.getAll(page, size, sortBy, sortDir, type, status, search);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"/{id}", "/get-by-id/{id}"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = budgetCategoryService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping({"/{id}", "/update/{id}"})
    @PreAuthorize("hasPermission(null, 'budget_category:manage')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody BudgetCategoryUpdateRequest request) {
        var response = budgetCategoryService.update(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping({"/{id}", "/delete/{id}"})
    @PreAuthorize("hasPermission(null, 'budget_category:manage')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = budgetCategoryService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
