package com.EduePoa.EP.MPesa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Mpesa Controller
@Slf4j
@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class MPesaController {

    private final MpesaServiceInterface mpesaService;

    @PostMapping("/initiate-payment")
    public MpesaPaymentResponseDTO initiatePayment(@RequestBody MpesaPaymentRequestDTO body) {


        System.out.println("Received request: Amount = " + body.getAmounts() + ", Phone Number = " + body.getPhoneNumber());

        return mpesaService.initiateSTKPush(body.getPhoneNumber(), body.getAmounts() ,body.getAccountReference());
    }



//    @RequestMapping(
//            path = "/stk-callback",
//            method = RequestMethod.POST,
//            produces = MediaType.APPLICATION_JSON_VALUE
//    )
//    public void stkPushCallback(@RequestBody Object object){
//        log.info(String.format("STK PUSH Callback Response: %s ", object.toString()));
//
//        this.mpesaService.processCallback(object);
//    }
//
////    @GetMapping("/transaction-status/{transactionId}")
////    public ResponseEntity<CustomResponse<?>> queryTransactionStatus(@PathVariable String transactionId) {
////        CustomResponse<?> response = mpesaService.queryTransactionStatus(transactionId);
////        return ResponseEntity.ok(response);
////
////    }
    @GetMapping("generate-token")
    public ResponseEntity<String> generateToken(){
        String response = mpesaService.generateToken();
        return ResponseEntity.ok(response);
    }
}
