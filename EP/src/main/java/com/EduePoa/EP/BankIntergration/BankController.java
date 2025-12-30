package com.EduePoa.EP.BankIntergration;

import com.EduePoa.EP.BankIntergration.BankRequest.BankRequestDTO;
import lombok.RequiredArgsConstructor;
import org.modelmapper.internal.bytebuddy.asm.Advice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bank")
@RequiredArgsConstructor
public class BankController {
    private final BankService bankService;



    @CrossOrigin("* ")
    @PostMapping("/post-transactions/")
    ResponseEntity<?>postTransactions( @RequestBody BankRequestDTO bankRequestDTO){
        var response = bankService.postTransactions(bankRequestDTO);
        return  ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("/get-transactions")
    ResponseEntity<?>getTransactions( ){
        var response = bankService.getTransactions();
        return  ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
