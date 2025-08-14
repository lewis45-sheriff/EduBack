package com.EduePoa.EP.Authentication.Auth;


import com.EduePoa.EP.Authentication.Auth.Request.LoginRequest;
import com.EduePoa.EP.Authentication.Auth.Request.RequestOTP;
import com.EduePoa.EP.Authentication.Auth.Request.ValidateOtp;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor

public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    ResponseEntity<?>login(@RequestBody LoginRequest loginRequest){
        var response = authService.login(loginRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("validate-otp")
    ResponseEntity<?>generateOtp(@RequestBody ValidateOtp validateOtp){
        var response = authService.validateOtp(validateOtp);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("/request-otp")
    ResponseEntity<?>requestOtp(@RequestBody RequestOTP requestOTP){
        var response = authService.requestOtp(requestOTP);
        return ResponseEntity.status(response.getStatusCode()).body(response);

    }
}
