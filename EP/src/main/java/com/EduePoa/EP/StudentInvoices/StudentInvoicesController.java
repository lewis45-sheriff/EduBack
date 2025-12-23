package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/invoicing/")
@RequiredArgsConstructor
public class StudentInvoicesController {
    private final StudentInvoicesService studentInvoicesService;

    @PostMapping("create/{studentId}/{term}")
    ResponseEntity<?> create(@PathVariable Long studentId, @PathVariable String term){
        var response = studentInvoicesService.create(studentId ,term);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("invoice-all")
    ResponseEntity<?> invoiceAll(@RequestParam String term){
        var response = studentInvoicesService.invoiceAll(term);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-invoives")
    ResponseEntity<?> getAllInvoices(){
        var response = studentInvoicesService. getAllInvoices();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


}
