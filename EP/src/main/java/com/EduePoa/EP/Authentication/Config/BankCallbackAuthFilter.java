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

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class BankCallbackAuthFilter extends OncePerRequestFilter {

    private final BankCallbackAuthConfig authConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only apply to bank callback endpoint
        if (!requestPath.equals("http://localhost:8085/api/v1/bank/post-transactions/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Bank callback request without Authorization header from IP: {}", getClientIP(request));
            sendUnauthorizedResponse(response, "Missing Authorization header");
            return;
        }

        if (!authHeader.startsWith("Basic ")) {
            log.warn("Bank callback request with invalid Authorization format from IP: {}", getClientIP(request));
            sendUnauthorizedResponse(response, "Invalid Authorization header format");
            return;
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                log.warn("Invalid credentials format from IP: {}", getClientIP(request));
                sendUnauthorizedResponse(response, "Invalid credentials format");
                return;
            }

            String username = parts[0];
            String password = parts[1];

            if (!authConfig.getUsername().equals(username) ||
                    !authConfig.getPassword().equals(password)) {
                log.warn("Invalid credentials attempt from IP: {} with username: {}",
                        getClientIP(request), username);
                sendUnauthorizedResponse(response, "Invalid username or password");
                return;
            }

            log.info("Bank callback authentication successful from IP: {} with username: {}",
                    getClientIP(request), username);

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {
            log.error("Error decoding Authorization header from IP: {}", getClientIP(request));
            sendUnauthorizedResponse(response, "Invalid Authorization header encoding");
        } catch (Exception e) {
            log.error("Error during authentication from IP: {}", getClientIP(request), e);
            sendUnauthorizedResponse(response, "Authentication failed");
        }
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

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
        return request.getRemoteAddr();
    }
}