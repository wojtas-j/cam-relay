package com.camrelay.handler;

import com.camrelay.dto.socket.SignalingMessage;
import com.camrelay.service.SignalingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            log.warn("Connection established without username attribute, closing");
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized")); } catch (Exception ignored) {}
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

            if ("ping".equals(msg.getType())) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                return;
            }

            // minimal validation
            if (msg.getType() == null || msg.getFrom() == null || msg.getTo() == null) {
                log.warn("Invalid signaling message received: {}", payload);
                return;
            }

            // only allow sender attribute equal to session username
            String sessionUser = (String) session.getAttributes().get("username");
            if (!msg.getFrom().equals(sessionUser)) {
                log.warn("Impersonation attempt: message from {} but session user {}", msg.getFrom(), sessionUser);
                return;
            }

            // route message to target (offer/answer/candidate)
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
    }
}
