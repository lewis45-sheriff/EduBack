package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("api/v1/finance-transactions/")
@RequiredArgsConstructor
@RestController
public class FinanceTransactionController {
    private final FinanceTransactionService financeTransactionService;

    /**
     * Manually record a finance transaction, optionally with a supporting document
     * (PNG/JPG/PDF/Word). Sent as multipart/form-data: a {@code data} part (JSON) and
     * an optional {@code attachment} file part. Gated by {@code transaction:create}.
     */
    @PreAuthorize("hasPermission(null, 'transaction:create')")
    @PostMapping(value = "create-transaction/{studentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> createTransaction(
            @PathVariable Long studentId,
            @RequestPart("data") CreateTransactionDTO createTransactionDTO,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment) {
        var response = financeTransactionService.createManualTransaction(studentId, createTransactionDTO, attachment);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    // ---- Maker-checker for manual transactions (active when the flag is enabled) ----

    @PreAuthorize("hasPermission(null, 'transaction:approve')")
    @PutMapping("pending/{pendingId}/approve")
    ResponseEntity<?> approvePending(@PathVariable Long pendingId) {
        var response = financeTransactionService.approvePendingTransaction(pendingId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction:approve')")
    @PutMapping("pending/{pendingId}/reject")
    ResponseEntity<?> rejectPending(@PathVariable Long pendingId,
                                    @RequestParam(required = false) String reason) {
        var response = financeTransactionService.rejectPendingTransaction(pendingId, reason);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PreAuthorize("hasPermission(null, 'transaction:create') or hasPermission(null, 'transaction:approve')")
    @GetMapping("pending")
    ResponseEntity<?> listPending(
            @RequestParam(required = false)
            com.EduePoa.EP.FinanceTransaction.PendingTransaction.PendingTransactionStatus status) {
        var response = financeTransactionService.listPendingTransactions(status);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-transactions")
    ResponseEntity<?> getTransactions(){
        var response = financeTransactionService.getTransactions();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-by-studentId/{studentId}")
    ResponseEntity<?> getByStudentId(@PathVariable Long studentId){
        var response = financeTransactionService.getByStudentId(studentId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-by-id/{id}")
    ResponseEntity<?> getById(@PathVariable Long id){
        var response = financeTransactionService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-statistic")
    ResponseEntity<?> getStatistics(){
        var response = financeTransactionService.getStatistics();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-student-payment/{studentId}")
    ResponseEntity<?> getStudentPayment(@PathVariable Long studentId){
        var response = financeTransactionService. getStudentPayment(studentId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("get-student-balance/{studentId}")
    ResponseEntity<?> getStudentBalance(@PathVariable Long studentId){
        var response = financeTransactionService.getStudentBalance(studentId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }






}
