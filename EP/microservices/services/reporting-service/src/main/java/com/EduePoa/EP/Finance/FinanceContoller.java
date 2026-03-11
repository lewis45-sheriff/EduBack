package com.EduePoa.EP.Finance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/finance")
@RequiredArgsConstructor
public class FinanceContoller {
    private final FinanceService financeService;

    @GetMapping("get-students-with-balances")
    ResponseEntity<?> getStudentsWithBalances(){
        var response = financeService. getStudentsWithBalances();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-student-with-balances/{studentId}")
    ResponseEntity<?> getStudentsWithBalancePerStudent(@PathVariable Long studentId){
        var response = financeService.  getStudentsWithBalancePerStudent(studentId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
