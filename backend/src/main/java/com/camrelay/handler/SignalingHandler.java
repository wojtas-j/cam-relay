package com.camrelay.handler;

import com.camrelay.dto.socket.SignalingMessage;
import com.camrelay.service.SignalingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * WebSocket handler for signaling channel. Receives JSON messages and forwards them via SignalingService.
 * Accepts messages: {type, from, to, payload}.
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalingHandler extends TextWebSocketHandler {

    private final SignalingServiceImpl signalingService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> ALLOWED_TYPES =
            Set.of("offer", "answer", "candidate", "ping");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Authentication auth = (Authentication) Objects.requireNonNull(session.getPrincipal());
        Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

        boolean isReceiver = roles.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RECEIVER"));

        boolean isNonReceiver = roles.stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_RECEIVER"));

        if (isReceiver && signalingService.countReceivers() >= 1) {
            close(session, "Receiver limit reached");
            return;
        }

        if (isNonReceiver && signalingService.countNonReceivers() >= 1) {
            close(session, "User/Admin limit reached");
            return;
        }

        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            close(session, "Unauthorized");
            return;
        }

        signalingService.register(username, session);
        log.info("Websocket connection established for {}", username);
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        try {
            String payload = message.getPayload();
            SignalingMessage msg = mapper.readValue(payload.getBytes(StandardCharsets.UTF_8), SignalingMessage.class);

            if (msg.getType() == null || !ALLOWED_TYPES.contains(msg.getType())) {
                log.warn("Invalid message type: {}", msg.getType());
                return;
            }

            if ("ping".equals(msg.getType())) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                return;
            }

            if (msg.getFrom() == null || msg.getTo() == null) {
                log.warn("Missing from/to fields: {}", payload);
                return;
            }

            String sessionUser = (String) session.getAttributes().get("username");
            if (!msg.getFrom().equals(sessionUser)) {
                log.warn("Impersonation attempt: msg.from={} but session user={}", msg.getFrom(), sessionUser);
                return;
            }

            signalingService.route(msg);

        } catch (Exception e) {
            log.error("Error handling websocket message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) signalingService.unregister(username);
        log.info("Websocket closed for {} with status {}", username, status);
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Transport error on websocket: {}", exception.getMessage(), exception);

        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            signalingService.unregister(username);
        }
    }

    private void close(WebSocketSession s, String reason) {
        try { s.close(CloseStatus.POLICY_VIOLATION.withReason(reason)); }
        catch (Exception ignored) {}
    }
}
