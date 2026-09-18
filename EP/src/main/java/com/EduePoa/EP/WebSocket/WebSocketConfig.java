package com.EduePoa.EP.WebSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.ChannelRegistration;

/**
 * STOMP-over-WebSocket configuration used to stream real-time progress of
 * long-running operations (currently the student bulk upload) to the frontend.
 *
 * <p>Clients:</p>
 * <ul>
 *   <li>Connect to the handshake endpoint {@code /ws} (SockJS fallback enabled).</li>
 *   <li>Authenticate by sending the JWT in the STOMP {@code Authorization} header
 *       on the CONNECT frame (see {@link StompAuthChannelInterceptor}).</li>
 *   <li>Subscribe to {@code /topic/bulk-upload/{jobId}} to receive progress events.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Allow the frontend origin(s) to open the handshake. Tighten this
                // to your actual frontend origin(s) in production.
                .setAllowedOriginPatterns("*")
                // Lifts the JWT from the accessToken cookie into the session.
                .addInterceptors(authHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker. Destinations the server broadcasts to.
        registry.enableSimpleBroker("/topic");
        // Prefix for messages bound for @MessageMapping-annotated methods (not used yet).
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authenticate the STOMP CONNECT frame with the JWT.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
