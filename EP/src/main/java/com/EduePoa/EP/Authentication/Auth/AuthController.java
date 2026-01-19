package com.EduePoa.EP.Authentication.Auth;


import com.EduePoa.EP.Authentication.Auth.Request.LoginRequest;
import com.EduePoa.EP.Authentication.Auth.Request.RequestOTP;
import com.EduePoa.EP.Authentication.Auth.Request.ResetPassword;
import com.EduePoa.EP.Authentication.Auth.Request.ValidateOtp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    ResponseEntity<?>login(@RequestBody LoginRequest loginRequest, HttpServletResponse httpResponse, HttpServletRequest httpRequest){
        var response = authService.login(loginRequest,httpResponse,httpRequest);
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
    @PostMapping("/reset-password")
    ResponseEntity<?>resetPassword(@RequestBody ResetPassword requestOTP){
        var response = authService.resetPassword(requestOTP);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("/log-out")
    ResponseEntity<?>logOut(HttpServletResponse httpResponse, HttpServletRequest httpRequest){
        var response = authService.logOut(httpResponse,httpRequest);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
