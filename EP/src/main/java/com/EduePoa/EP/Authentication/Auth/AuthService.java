package com.EduePoa.EP.Authentication.Auth;

import com.EduePoa.EP.Authentication.Auth.Request.LoginRequest;
import com.EduePoa.EP.Authentication.Auth.Request.RequestOTP;
import com.EduePoa.EP.Authentication.Auth.Request.ResetPassword;
import com.EduePoa.EP.Authentication.Auth.Request.ValidateOtp;
import com.EduePoa.EP.Authentication.Auth.Response.AuthResponse;
import com.EduePoa.EP.Authentication.Config.CookieService;
import com.EduePoa.EP.Authentication.Config.TokenBlacklistService;
import com.EduePoa.EP.Authentication.Email.EmailService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.JWT.JwtService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.Utils.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private static final long ACCESS_TOKEN_DURATION = 24 * 60 * 60 * 1000;
    private static final long REFRESH_TOKEN_DURATION = 7 * 24 * 60 * 60 * 1000;
    private final PasswordEncoder passwordEncoder;
    private final CookieService cookieService;
    private final TokenBlacklistService tokenBlacklistService;


    CustomResponse<?> login(LoginRequest loginRequest, HttpServletResponse httpResponse, HttpServletRequest httpRequest) {
        CustomResponse<AuthResponse> response = new CustomResponse<>();

        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );

            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // Check if password reset is required
            if (user.getPassword() == null || user.getPassword().trim().isEmpty() || Boolean.TRUE.equals(user.getPasswordReset())) {
                user.setPasswordReset(true);
                userRepository.save(user);

                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Password reset required. Please reset your password to continue.");
                response.setEntity(null);
                return response;
            }

            // Check if user account is active
            if (user.getStatus().equals(Status.INACTIVE)) {
                response.setStatusCode(HttpStatus.FORBIDDEN.value());
                response.setMessage("User Account is Inactive. Please Contact System Admin");
                response.setEntity(null);
                return response;
            }

            // Generate OTP
            String otp = String.valueOf((int) ((Math.random() * 900000) + 100000)); // 6-digit OTP
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5); // OTP valid for 5 mins

            // Save OTP in user
            user.setOtpCode(otp);
            user.setOtpExpiry(expiryTime);
            userRepository.save(user);

            // Send OTP email
            String subject = "Your Login OTP";
            String body = "<p>Hello " + user.getFirstName() + ",</p>"
                    + "<p>Your one-time password (OTP) is: <b>" + otp + "</b></p>"
                    + "<p>This OTP will expire in 5 minutes.</p>"
                    + "<br><p>Regards,<br>Security Team</p>";

            // emailService.sendEmail(user.getEmail(), subject, body);
            log.info("OTP generated for user: {}", user.getEmail());

            // Invalidate old session
            try {
                HttpSession oldSession = httpRequest.getSession(false);
                if (oldSession != null) {
                    log.debug("Invalidating old session: {}", oldSession.getId());
                    oldSession.invalidate();
                }
            } catch (Exception e) {
                log.warn("Could not invalidate old session: {}", e.getMessage());
            }

            // Clear existing cookies
            cookieService.deleteAuthCookies(httpResponse);

            // Clear SecurityContext
            SecurityContextHolder.clearContext();

            // Create new session
            HttpSession newSession = httpRequest.getSession(true);
            log.debug("Created new session: {}", newSession.getId());

            // Generate JWT tokens
            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("type", "ACCESS");
            String jwtToken = jwtService.generateJwtToken(accessClaims, user, ACCESS_TOKEN_DURATION);

            Map<String, Object> refreshClaims = new HashMap<>();
            refreshClaims.put("type", "REFRESH");
            String refreshToken = jwtService.generateJwtToken(refreshClaims, user, REFRESH_TOKEN_DURATION);

            // Set tokens in cookies
            cookieService.createAccessTokenCookie(httpResponse, jwtToken);
            cookieService.createRefreshTokenCookie(httpResponse, refreshToken);

            // Set authentication in SecurityContext
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    jwtToken,
                    user.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Set authentication for user: {}", user.getUsername());


            // Build AuthResponse (tokens are in cookies, not in response body)
            AuthResponse authResponse = AuthResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstname(user.getFirstName())
                    .lastname(user.getLastName())
                    .role(user.getRole().getName())
                    .phoneNumber(user.getPhoneNumber())
                    .passwordReset(user.getPasswordReset())
                    .build();


            response.setEntity(authResponse);
            response.setMessage("Login successful. OTP sent to your email.");
            response.setStatusCode(HttpStatus.OK.value());

            log.info("Login successful for user: {}", user.getEmail());

        } catch (AuthenticationException e) {
            log.error("Authentication failed: {}", e.getMessage());
            response.setMessage("Invalid email or password");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.UNAUTHORIZED.value());
        } catch (ResourceNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage(), e);
            response.setMessage("An error occurred during login. Please try again.");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }



    CustomResponse<?> resetPassword(ResetPassword resetPassword) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            // Validate request
            if (resetPassword.getEmail() == null || resetPassword.getEmail().trim().isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Email is required");
                response.setEntity(null);
                return response;
            }

            if (resetPassword.getOldPassword() == null || resetPassword.getOldPassword().trim().isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Old password is required");
                response.setEntity(null);
                return response;
            }

            if (resetPassword.getNewPassword() == null || resetPassword.getNewPassword().trim().isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("New password is required");
                response.setEntity(null);
                return response;
            }

            // Check if new password is at least 8 characters
            if (resetPassword.getNewPassword().length() < 8) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("New password must be at least 8 characters long");
                response.setEntity(null);
                return response;
            }

            // Check if old and new passwords are different
            if (resetPassword.getOldPassword().equals(resetPassword.getNewPassword())) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("New password must be different from old password");
                response.setEntity(null);
                return response;
            }

            // Find user by email
            User user = userRepository.findByEmail(resetPassword.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + resetPassword.getEmail()));

            // Check if user account is active
            if (user.getStatus().equals(Status.INACTIVE)) {
                response.setStatusCode(HttpStatus.FORBIDDEN.value());
                response.setMessage("User account is inactive. Please contact system admin");
                response.setEntity(null);
                return response;
            }

            // Verify old password
            if (!passwordEncoder.matches(resetPassword.getOldPassword(), user.getPassword())) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Old password is incorrect");
                response.setEntity(null);
                return response;
            }

            // Encode and set new password
            String encodedNewPassword = passwordEncoder.encode(resetPassword.getNewPassword());
            user.setPassword(encodedNewPassword);

            // Set passwordReset flag to false since user has successfully reset their password
            user.setPasswordReset(false);

            // Set forcePasswordReset to false as well
//            user.setForcePasswordReset(false);

            // Update timestamp
            user.setUpdatedOn(new Timestamp(System.currentTimeMillis()));

            // Save user
            userRepository.save(user);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Password reset successful. Please login with your new password");
            response.setEntity(null);

        } catch (ResourceNotFoundException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("An error occurred while resetting password: " + e.getMessage());
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
    CustomResponse<?> logOut(HttpServletResponse httpResponse, HttpServletRequest httpRequest) {
        CustomResponse<?> response = new CustomResponse<>();

        try {
            String username = "UNKNOWN_USER";
            String accessToken = null;
            String refreshToken = null;

            // Extract access token from cookie
            accessToken = cookieService.getAccessToken(httpRequest);

            // Fallback: Extract from Authorization header if not in cookie
            if (accessToken == null || accessToken.isBlank()) {
                String authHeader = httpRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    accessToken = authHeader.substring(7);
                }
            }

            // Extract refresh token from cookie
            refreshToken = cookieService.getRefreshToken(httpRequest);

            // Extract user info from access token
            if (accessToken != null && !accessToken.isBlank()) {
                try {
                    username = jwtService.extractUsername(accessToken);

                    // Blacklist the access token
                    tokenBlacklistService.blacklistToken(accessToken);
                    log.debug("Access token blacklisted for user: {}", username);

                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    // Token expired - extract claims from exception
                    try {
                        username = e.getClaims().getSubject();
                        log.debug("Extracted info from expired token for user: {}", username);
                    } catch (Exception ex) {
                        log.warn("Could not extract claims from expired token: {}", ex.getMessage());
                    }
                    // Blacklist even if expired
                    tokenBlacklistService.blacklistToken(accessToken);

                } catch (Exception e) {
                    log.warn("Could not extract user info from access token: {}", e.getMessage());
                }
            }

            // Blacklist refresh token
            if (refreshToken != null && !refreshToken.isBlank()) {
                try {
                    tokenBlacklistService.blacklistToken(refreshToken);
                    log.debug("Refresh token blacklisted");

                    // Try to get username from refresh token if not found in access token
                    if ("UNKNOWN_USER".equals(username)) {
                        username = jwtService.extractUsername(refreshToken);
                    }
                } catch (Exception e) {
                    log.warn("Could not blacklist refresh token: {}", e.getMessage());
                }
            }

            // Fallback: Get username from SecurityContext
            if ("UNKNOWN_USER".equals(username)) {
                try {
                    username = JwtService.getUsername();
                    if (username == null || username.isBlank()) {
                        username = "UNKNOWN_USER";
                    }
                } catch (Exception e) {
                    log.debug("Could not get username from SecurityContext: {}", e.getMessage());
                }
            }

            // Invalidate HTTP session
            try {
                HttpSession session = httpRequest.getSession(false);
                if (session != null) {
                    log.debug("Invalidating session: {}", session.getId());
                    session.invalidate();
                }
            } catch (Exception e) {
                log.warn("Could not invalidate session: {}", e.getMessage());
            }

            // Clear all authentication cookies
            cookieService.deleteAuthCookies(httpResponse);

            // Clear Spring Security context
            SecurityContextHolder.clearContext();

            // Audit log
//            auditService.logAction("LOGOUT", "User", username, "User logged out successfully");

            log.info("User '{}' logged out successfully", username);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Logout successful");
            response.setEntity(null);

        } catch (Exception e) {
            log.error("Error during logout process: {}", e.getMessage(), e);

            // Emergency cleanup - clear cookies and context even on error
            try {
                cookieService.deleteAuthCookies(httpResponse);
                SecurityContextHolder.clearContext();
            } catch (Exception cleanupError) {
                log.error("Failed to cleanup during error handling: {}", cleanupError.getMessage());
            }

            // Always return success to prevent information leakage
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Logout successful");
            response.setEntity(null);
        }

        return response;
    }



}
