package com.EduePoa.EP.Authentication.Config;

import com.EduePoa.EP.BankIntergration.BankRequest.BankCallbackAuthConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class BankCallbackAuthFilter extends OncePerRequestFilter {

    private final BankCallbackAuthConfig authConfig;

    // Rate limiting: Track failed attempts per IP
    private final ConcurrentHashMap<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        //  Use path matching instead of full URL
        if (!requestPath.startsWith("/api/v1/bank/post-transactions/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIP = getClientIP(request);

        // Check if IP is locked out due to failed attempts
        if (isLockedOut(clientIP)) {
            log.warn("Bank callback request from locked out IP: {}", clientIP);
            sendUnauthorizedResponse(response, "Too many failed attempts. Account locked for 15 minutes.");
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Bank callback request without Authorization header from IP: {}", clientIP);
            recordFailedAttempt(clientIP);
            sendUnauthorizedResponse(response, "Missing Authorization header");
            return;
        }

        if (!authHeader.startsWith("Basic ")) {
            log.warn("Bank callback request with invalid Authorization format from IP: {}", clientIP);
            recordFailedAttempt(clientIP);
            sendUnauthorizedResponse(response, "Invalid Authorization header format");
            return;
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                log.warn("Invalid credentials format from IP: {}", clientIP);
                recordFailedAttempt(clientIP);
                sendUnauthorizedResponse(response, "Invalid credentials format");
                return;
            }

            String username = parts[0];
            String password = parts[1];

            // Validate credentials
            if (!authConfig.getUsername().equals(username) ||
                    !authConfig.getPassword().equals(password)) {
                log.warn("Invalid credentials attempt from IP: {} with username: {}", clientIP, username);
                recordFailedAttempt(clientIP);
                sendUnauthorizedResponse(response, "Invalid username or password");
                return;
            }

            // Authentication successful - clear failed attempts
            clearFailedAttempts(clientIP);
            log.info("Bank callback authentication successful from IP: {} with username: {}", clientIP, username);

            // Add security headers
            addSecurityHeaders(response);

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {
            log.error("Error decoding Authorization header from IP: {}", clientIP);
            recordFailedAttempt(clientIP);
            sendUnauthorizedResponse(response, "Invalid Authorization header encoding");
        } catch (Exception e) {
            log.error("Error during authentication from IP: {}", clientIP, e);
            recordFailedAttempt(clientIP);
            sendUnauthorizedResponse(response, "Authentication failed");
        }
    }

    private boolean isLockedOut(String clientIP) {
        FailedAttempt attempt = failedAttempts.get(clientIP);
        if (attempt == null) {
            return false;
        }

        long lockoutEndTime = attempt.lastAttemptTime + TimeUnit.MINUTES.toMillis(LOCKOUT_DURATION_MINUTES);
        if (System.currentTimeMillis() > lockoutEndTime) {
            // Lockout period expired, clear the record
            failedAttempts.remove(clientIP);
            return false;
        }

        return attempt.count >= MAX_FAILED_ATTEMPTS;
    }

    private void recordFailedAttempt(String clientIP) {
        failedAttempts.compute(clientIP, (key, attempt) -> {
            if (attempt == null) {
                return new FailedAttempt(1, System.currentTimeMillis());
            }

            // Reset count if last attempt was more than lockout duration ago
            long timeSinceLastAttempt = System.currentTimeMillis() - attempt.lastAttemptTime;
            if (timeSinceLastAttempt > TimeUnit.MINUTES.toMillis(LOCKOUT_DURATION_MINUTES)) {
                return new FailedAttempt(1, System.currentTimeMillis());
            }

            return new FailedAttempt(attempt.count + 1, System.currentTimeMillis());
        });

        FailedAttempt currentAttempt = failedAttempts.get(clientIP);
        if (currentAttempt.count >= MAX_FAILED_ATTEMPTS) {
            log.error("IP {} has been locked out after {} failed attempts", clientIP, currentAttempt.count);
        }
    }

    private void clearFailedAttempts(String clientIP) {
        failedAttempts.remove(clientIP);
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add security headers even for error responses
        addSecurityHeaders(response);

        String jsonResponse = String.format(
                "{\"statusCode\": 401, \"message\": \"%s\", \"entity\": null}",
                message
        );

        response.getWriter().write(jsonResponse);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    // Inner class to track failed attempts
    private static class FailedAttempt {
        final int count;
        final long lastAttemptTime;

        FailedAttempt(int count, long lastAttemptTime) {
            this.count = count;
            this.lastAttemptTime = lastAttemptTime;
        }
    }
}