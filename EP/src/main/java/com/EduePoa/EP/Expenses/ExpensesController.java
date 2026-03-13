package com.EduePoa.EP.Expenses;

import com.EduePoa.EP.Expenses.Requests.ExpenseRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpensesController {

    private final ExpensesService expensesService;

    // POST /api/v1/expenses  (also: POST /create)
    @PostMapping({"", "/create"})
    public ResponseEntity<?> create(@Valid @RequestBody ExpenseRequestDTO requestDTO) {
        var response = expensesService.create(requestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/get-all"})
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        var response = expensesService.getAll(page, size, sortBy, sortDir,
                search, category, status, paymentMethod, startDate, endDate);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping({"/summary", "/stats"})
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        var response = expensesService.getSummary(startDate, endDate);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // GET /api/v1/expenses/{id}  (also: GET /get-by-id/{id})
    @GetMapping({"/{id}", "/get-by-id/{id}"})
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = expensesService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // PUT /api/v1/expenses/{id}  (also: PUT /update/{id}, PUT /edit/{id})
    @PutMapping({"/{id}", "/update/{id}", "/edit/{id}"})
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody ExpenseRequestDTO requestDTO) {
        var response = expensesService.update(id, requestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // DELETE /api/v1/expenses/{id}  (also: DELETE /delete/{id})
    @DeleteMapping({"/{id}", "/delete/{id}"})
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var response = expensesService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // PATCH /api/v1/expenses/approve/{id}
    @PatchMapping("/approve/{id}")
    public ResponseEntity<?> approveExpense(@PathVariable Long id) {
        var response = expensesService.approveExpense(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
