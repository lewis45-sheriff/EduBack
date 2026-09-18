package com.EduePoa.EP.WebSocket;

import com.EduePoa.EP.Authentication.JWT.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP clients on the CONNECT frame using the same JWT the REST
 * API uses. The client must send the token in an {@code Authorization} header
 * (value {@code "Bearer <token>"} or the raw token) on the CONNECT frame.
 *
 * <p>On success the resolved {@link java.security.Principal} is attached to the
 * STOMP session and the tenant identifier is stored in the session attributes as
 * {@code tenantId}, so downstream code (and topic authorization) can use them.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String TENANT_ATTRIBUTE = "tenantId";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Only authenticate on CONNECT; other frames reuse the session's principal.
            return message;
        }

        // Primary: JWT lifted from the accessToken cookie during the handshake.
        // Fallback: STOMP Authorization header (for non-cookie clients / tooling).
        String token = null;
        if (accessor.getSessionAttributes() != null) {
            Object cookieToken = accessor.getSessionAttributes()
                    .get(AuthHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE);
            if (cookieToken instanceof String s && !s.isBlank()) {
                token = s;
            }
        }
        if (token == null) {
            token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
        }
        if (token == null) {
            log.warn("STOMP CONNECT rejected: no JWT found (neither accessToken cookie nor Authorization header)");
            throw new IllegalArgumentException("Missing authentication token");
        }

        String username = jwtService.extractUsername(token);
        if (username == null) {
            log.warn("STOMP CONNECT rejected: invalid/expired token");
            throw new IllegalArgumentException("Invalid or expired token");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            log.warn("STOMP CONNECT rejected: token failed validation for user {}", username);
            throw new IllegalArgumentException("Invalid or expired token");
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, token, userDetails.getAuthorities());
        accessor.setUser(auth);

        String tenantId = jwtService.extractTenantId(token);
        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put(TENANT_ATTRIBUTE, tenantId);
        }

        log.debug("STOMP CONNECT authenticated for user '{}' (tenant '{}')", username, tenantId);
        return message;
    }

    private String resolveToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value.isEmpty() ? null : value;
    }
}
