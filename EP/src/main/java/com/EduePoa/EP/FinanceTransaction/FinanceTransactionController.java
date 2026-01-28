package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/v1/finance-transactions/")
@RequiredArgsConstructor
@RestController
public class FinanceTransactionController {
    private final FinanceTransactionService financeTransactionService;
    @PostMapping("create-transaction/{studentId}")
    ResponseEntity<?> createTransaction(@PathVariable Long studentId ,@RequestBody CreateTransactionDTO createTransactionDTO){
        var response = financeTransactionService.createTransaction(studentId,createTransactionDTO);
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






}
