package com.camrelay.configuration;

import com.camrelay.service.JwtTokenProviderImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Handshake interceptor which validates JWT access token present in cookies during the WebSocket handshake.
 * If valid, places the username into the WebSocket session attributes under key "username".
 * If invalid, prevents the handshake by returning false.
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProviderImpl jwtTokenProvider;

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request,
                                   @NotNull ServerHttpResponse response,
                                   @NotNull WebSocketHandler wsHandler,
                                   @NotNull Map<String, Object> attributes) throws Exception {
        if (!(request instanceof ServletServerHttpRequest servletReq)) {
            log.warn("Non-servlet request during websocket handshake");
            return false;
        }

        HttpServletRequest httpReq = servletReq.getServletRequest();

        String token = null;
        if (httpReq.getCookies() != null) {
            for (Cookie c : httpReq.getCookies()) {
                if ("accessToken".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token == null || token.isBlank()) {
            log.warn("Missing accessToken cookie during websocket handshake");
            return false;
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("Invalid accessToken during websocket handshake");
                return false;
            }
            String username = jwtTokenProvider.getUsernameFromToken(token);
            attributes.put("username", username);
            log.info("WebSocket handshake: user '{}' accepted", username);
            return true;
        } catch (Exception ex) {
            log.warn("JWT validation failed during handshake: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request,
                               @NotNull ServerHttpResponse response,
                               @NotNull WebSocketHandler wsHandler, Exception exception) {
    }
}
