package com.EduePoa.EP.MpesaPaybill;

import com.EduePoa.EP.MpesaPaybill.Requests.ConfirmationRequest;
import com.EduePoa.EP.MpesaPaybill.Requests.RegisterRequest;
import com.EduePoa.EP.MpesaPaybill.Requests.ValidationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/")
@RequiredArgsConstructor
public class MpesaPaybillController {
    private final MpesaPaybillService mpesaPaybillService;
    @PostMapping("register-url")
    ResponseEntity<?>registerUrl(@RequestBody RegisterRequest registerRequest){
        var response = mpesaPaybillService.registerUrl(registerRequest);
        return  ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("generate-token")
    public ResponseEntity<String> generateToken(){
        String response = mpesaPaybillService.generateToken();
        return ResponseEntity.ok(response);
    }
    @PostMapping(  value = "/validate",
            consumes = "application/json",
            produces = "application/json")
    ResponseEntity<?>validate(@RequestBody ValidationRequest request){
        var response = mpesaPaybillService.validate(request);
        return  ResponseEntity.status(response.getStatusCode()).body(response);

    }
    @PostMapping( value = "/process-call-back",
            consumes = "application/json",
            produces = "application/json")
    ResponseEntity<?>processCallBack(@RequestBody ConfirmationRequest request){
        var response = mpesaPaybillService.processCallBack(request);
        return  ResponseEntity.status(response.getStatusCode()).body(response);
    }
//    @PostMapping("simulate-transaction")
//    ResponseEntity<?>simulateTransaction(@RequestBody ConfirmationRequest request){
//        var response = mpesaPaybillService.simulateTransaction(request);
//        return  ResponseEntity.status(response.getStatusCode()).body(response);
//    }


}
