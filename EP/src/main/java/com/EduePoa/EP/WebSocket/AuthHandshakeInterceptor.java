package com.EduePoa.EP.WebSocket;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the JWT from the {@code accessToken} HTTP-only cookie during the
 * WebSocket handshake and stores it in the WebSocket session attributes.
 *
 * <p>The token cannot be read by client-side JavaScript (HttpOnly), so it can't
 * be placed on the STOMP {@code Authorization} header directly. But the cookie
 * is sent automatically on the handshake HTTP request, so we lift it here and
 * hand it to {@link StompAuthChannelInterceptor} to authenticate the CONNECT
 * frame.</p>
 */
@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ACCESS_TOKEN_ATTRIBUTE = "accessToken";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())
                            && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                        attributes.put(ACCESS_TOKEN_ATTRIBUTE, cookie.getValue());
                        log.debug("WebSocket handshake: accessToken cookie captured");
                        break;
                    }
                }
            }
        }
        // Always allow the handshake; actual auth happens on the STOMP CONNECT frame.
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
