package com.EduePoa.EP.StudentInvoices;

import com.EduePoa.EP.Authentication.Enum.Term;
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
    @GetMapping("get-invoice-by-student/{id}")
    ResponseEntity<?> getInvoiceByStudentId(@PathVariable Long id){
        var response = studentInvoicesService. getAllInvoices(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-current-terms")
    ResponseEntity<?> getCurrentTermInvoices(){
        var response = studentInvoicesService.getCurrentTermInvoices();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("/term/{term}")
    public ResponseEntity<?> getInvoicesByTerm(@PathVariable Term term) {
       var response = studentInvoicesService.getInvoicesByTerm(term);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasPermission(null, 'invoice:reverse')")
    @PostMapping("reverse/{invoiceId}")
    ResponseEntity<?> reverseInvoice(@PathVariable Long invoiceId){
        var response = studentInvoicesService.reverseInvoice(invoiceId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasPermission(null, 'invoice:reverse')")
    @PostMapping("reverse/student/{studentId}/{term}")
    ResponseEntity<?> reverseStudentInvoice(@PathVariable Long studentId,
                                            @PathVariable Term term,
                                            @RequestParam(required = false) Integer academicYear){
        var response = studentInvoicesService.reverseInvoice(studentId, term, academicYear);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasPermission(null, 'invoice:reverse')")
    @PostMapping("reverse-all/{term}")
    ResponseEntity<?> reverseAllInvoices(@PathVariable Term term,
                                         @RequestParam(required = false) Integer academicYear){
        var response = studentInvoicesService.reverseAll(term, academicYear);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasPermission(null, 'invoice:reverse')")
    @PostMapping("reverse/grade/{gradeId}/{term}")
    ResponseEntity<?> reverseGradeInvoices(@PathVariable Long gradeId,
                                           @PathVariable Term term,
                                           @RequestParam(required = false) Integer academicYear){
        var response = studentInvoicesService.reverseByGrade(gradeId, term, academicYear);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasPermission(null, 'invoice:reverse')")
    @GetMapping("reversals")
    ResponseEntity<?> getReversals(){
        var response = studentInvoicesService.getReversals();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }




}
