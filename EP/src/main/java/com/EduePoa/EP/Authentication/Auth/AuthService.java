package com.EduePoa.EP.Authentication.Auth;

import com.EduePoa.EP.Authentication.Auth.Request.LoginRequest;
import com.EduePoa.EP.Authentication.Auth.Request.RequestOTP;
import com.EduePoa.EP.Authentication.Auth.Request.ValidateOtp;
import com.EduePoa.EP.Authentication.Auth.Response.AuthResponse;
import com.EduePoa.EP.Authentication.Email.EmailService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.JWT.JwtService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.Utils.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private static final long ACCESS_TOKEN_DURATION = 24 * 60 * 60 * 1000;
    private static final long REFRESH_TOKEN_DURATION = 7 * 24 * 60 * 60 * 1000;

    CustomResponse<?> login(LoginRequest loginRequest) {
        CustomResponse<AuthResponse> response = new CustomResponse<>();
        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );

            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Account setup incomplete. Please reset your password.");
                response.setEntity(null);
                return response;
            }

            if (user.getStatus().equals(Status.INACTIVE)) {
                response.setStatusCode(HttpStatus.FORBIDDEN.value());
                response.setMessage("User Account is Inactive Please Contact System Admin");
                response.setEntity(null);
                return response;
            }

            // ===== Generate OTP =====
            String otp = String.valueOf((int) ((Math.random() * 900000) + 100000)); // 6-digit OTP
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5); // OTP valid for 5 mins

            // Save OTP in user or separate table
            user.setOtpCode(otp);
            user.setOtpExpiry(expiryTime);
            userRepository.save(user);

            String subject = "Your Login OTP";
            String body = "<p>Hello " + user.getFirstName() + ",</p>"
                    + "<p>Your one-time password (OTP) is: <b>" + otp + "</b></p>"
                    + "<p>This OTP will expire in 5 minutes.</p>"
                    + "<br><p>Regards,<br>Security Team</p>";

            emailService.sendEmail(user.getEmail(), subject, body); // You create this service

            // ===== JWT Token Generation =====
            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("type", "ACCESS");
            String jwtToken = jwtService.generateJwtToken(accessClaims, user, ACCESS_TOKEN_DURATION);

            Map<String, Object> refreshClaims = new HashMap<>();
            refreshClaims.put("type", "REFRESH");
            String refreshToken = jwtService.generateJwtToken(refreshClaims, user, REFRESH_TOKEN_DURATION);

            AuthResponse authResponse = AuthResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstname(user.getFirstName())
                    .lastname(user.getLastName())
                    .accessToken(jwtToken)
                    .tokenExpiresIn(ACCESS_TOKEN_DURATION / 1000)
                    .refreshToken(refreshToken)
                    .refreshTokenExpiresIn(REFRESH_TOKEN_DURATION / 1000)
                    .phoneNumber(user.getPhoneNumber())
                    .role(user.getRole().getName())
                    .build();

            response.setEntity(authResponse);
            response.setMessage("Login successful. OTP sent to your registered phone/email.");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }
    CustomResponse<?> validateOtp(ValidateOtp validateOtp) {
        CustomResponse<String> response = new CustomResponse<>();
        try {
            //  Get logged-in user from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED.value());
                response.setMessage("User is not authenticated");
                return response;
            }

            String email = authentication.getName(); // assuming email is the username
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            //  Validate OTP exists and is not expired
            if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("No OTP found. Please request a new one.");
                return response;
            }

            if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("OTP expired. Please request a new one.");
                return response;
            }

            //  Compare OTP
            if (!user.getOtpCode().equals(String.valueOf(validateOtp.getOtp()))) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Invalid OTP");
                return response;
            }

            //  Clear OTP after successful validation
            user.setOtpCode(null);
            user.setOtpExpiry(null);
            userRepository.save(user);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("OTP validated successfully");
            response.setEntity("OTP validated successfully");

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }
    CustomResponse<?> requestOtp(RequestOTP requestOTP) {
        CustomResponse<String> response = new CustomResponse<>();
        try {
            //  Get the logged-in user
//            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//            if (authentication == null || !authentication.isAuthenticated()) {
//                response.setStatusCode(HttpStatus.UNAUTHORIZED.value());
//                response.setMessage("User is not authenticated");
//                return response;
//            }

            User user = userRepository.findByEmail(requestOTP.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            //  Generate OTP
            String otp = String.valueOf((int) ((Math.random() * 900000) + 100000)); // 6-digit
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5); // still 5 mins valid

            //  Save OTP
            user.setOtpCode(otp);
            user.setOtpExpiry(expiryTime);
            userRepository.save(user);

            //  Send OTP via email
            String subject = "Your New OTP Code";
            String body = "<p>Hello " + user.getFirstName() + ",</p>"
                    + "<p>Your new one-time password (OTP) is: <b>" + otp + "</b></p>"
                    + "<p>This OTP will expire in 5 minutes.</p>"
                    + "<br><p>Regards,<br>Security Team</p>";

            emailService.sendEmail(user.getEmail(), subject, body);

            //  Response
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("A new OTP has been sent to your registered email.");
            response.setEntity("OTP sent successfully");

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }

        return response;
    }



}
