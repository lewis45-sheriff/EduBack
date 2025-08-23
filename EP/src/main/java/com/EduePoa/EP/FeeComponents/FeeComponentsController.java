package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.FeeComponents.Requests.FeeComponentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/fee-components/")
@RequiredArgsConstructor
public class FeeComponentsController {
    private final  FeeComponentsService feeComponentsService;

    @PostMapping("create")
    ResponseEntity<?>create(@RequestBody FeeComponentRequest feeComponentRequest){
        var response = feeComponentsService.create(feeComponentRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all")
    ResponseEntity<?>getAll(){
        var response = feeComponentsService.getAll();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PatchMapping("delete/{id}")
    ResponseEntity<?>delete(@PathVariable Long id){
        var response = feeComponentsService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-fee-structure-formated")
    ResponseEntity<?>getAllForFeeStructure(){
        var response = feeComponentsService.getAllForFeeStructure();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
