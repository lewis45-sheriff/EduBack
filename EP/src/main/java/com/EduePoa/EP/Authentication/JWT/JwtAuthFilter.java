package com.EduePoa.EP.Authentication.JWT;


import com.EduePoa.EP.Authentication.Config.CookieService;
import com.EduePoa.EP.Authentication.Config.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CookieService cookieService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    // Comprehensive list of public paths that don't need JWT validation
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/reset-password",
            "/api/v1/bank/post-transactions/",
            "/api/v1/auth/request-otp",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",           // Add logout to public paths
            "/api/v1/auth/azure/",
            "/api/v1/auth/test/",
            "/oauth2/",
            "/login/oauth2/",
            "/error",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars",
            "/actuator/"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String path = request.getServletPath();
        log.debug("JWT Filter processing path: {}", path);

        // Skip JWT validation for public paths
        if (isPublicPath(path)) {
            log.debug("Skipping JWT validation for public path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token from either cookie or Authorization header
        String jwtToken = extractJwtToken(request);

        // No token provided - let Spring Security's exception handler deal with it
        if (jwtToken == null || jwtToken.isBlank()) {
            log.debug("No JWT token found for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // CRITICAL: Check if token is blacklisted (from logout)
        if (tokenBlacklistService.isBlacklisted(jwtToken)) {
            log.warn("Attempt to use blacklisted token for path: {}", path);

            // Clear the invalid cookies from the browser
            cookieService.deleteAuthCookies(response);

            // Clear security context
            SecurityContextHolder.clearContext();

            // Continue without authentication (will trigger 401)
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.debug("Processing JWT token for path: {}", path);

            final String username = jwtService.extractUsername(jwtToken);
            log.debug("Extracted username from token: {}", username);

            // Verify token type - only accept ACCESS tokens in the filter
            String tokenType = jwtService.extractClaim(jwtToken,
                    claims -> claims.get("type", String.class));

            if (!"ACCESS".equals(tokenType)) {
                log.warn("Invalid token type '{}' used for authentication. Expected ACCESS token", tokenType);
                cookieService.deleteAuthCookies(response);
                filterChain.doFilter(request, response);
                return;
            }

            Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

            // Only set authentication if not already authenticated
            if (username != null && (existingAuth == null
                    || !existingAuth.isAuthenticated()
                    || existingAuth instanceof AnonymousAuthenticationToken)) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                log.debug("Loaded user details for: {}", username);

                if (jwtService.isTokenValid(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            jwtToken,  // Store the full token as credentials
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT authentication successful for user: {}", username);
                } else {
                    log.warn("JWT token validation failed for user: {}", username);
                    // Clear invalid cookies
                    cookieService.deleteAuthCookies(response);
                }
            } else if (existingAuth != null && existingAuth.isAuthenticated()) {
                // Verify that the token matches the authenticated user
                String authenticatedUsername = existingAuth.getName();
                if (!username.equals(authenticatedUsername)) {
                    log.warn("Token username '{}' doesn't match authenticated user '{}'. Clearing authentication.",
                            username, authenticatedUsername);
                    cookieService.deleteAuthCookies(response);
                    SecurityContextHolder.clearContext();
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Expired JWT token for path: {}", path);
            // Clear expired cookies
            cookieService.deleteAuthCookies(response);
            SecurityContextHolder.clearContext();
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("Malformed JWT token for path: {}", path);
            cookieService.deleteAuthCookies(response);
            SecurityContextHolder.clearContext();
        } catch (io.jsonwebtoken.SignatureException e) {
            log.warn("Invalid JWT signature for path: {}", path);
            cookieService.deleteAuthCookies(response);
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("Unexpected JWT filter error for path: {}", path, e);
            cookieService.deleteAuthCookies(response);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtToken(HttpServletRequest request) {
        // First, try to get token from cookie
        String tokenFromCookie = cookieService.getAccessToken(request);
        if (tokenFromCookie != null && !tokenFromCookie.isBlank()) {
            log.debug("JWT token found in cookie");
            return tokenFromCookie;
        }

        // If no cookie, try Authorization header
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("JWT token found in Authorization header");
            return authHeader.substring(7);
        }

        return null;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();

        // Always skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("Skipping JWT filter for OPTIONS request");
            return true;
        }

        return false;
    }
}