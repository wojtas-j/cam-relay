package com.camrelay.configuration;

import com.camrelay.handler.SignalingHandler;
import com.camrelay.properties.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers websocket endpoint and handler for signaling.
 * @since 1.0
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;
    private final WebSocketProperties webSocketProperties;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, webSocketProperties.getPath())
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(webSocketProperties.getAllowedOrigins().toArray(new String[0]));
    }
}
