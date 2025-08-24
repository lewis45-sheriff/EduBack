package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.FeeStructure.Requests.FeeStructureRequestDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/fee-structure/")
@RequiredArgsConstructor
public class FeeStructureContoller {
    private final FeeStructureService feeStructureService;
    @PostMapping("create")
    ResponseEntity<?> create(@RequestBody FeeStructureRequestDTO feeStructureRequestDTO){
        var response = feeStructureService.create(feeStructureRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-fee-structures")
    ResponseEntity<?> getAllFeeStructures(){
        var response = feeStructureService.getAllFeeStructures();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
