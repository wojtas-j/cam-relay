package com.camrelay.service;

import com.camrelay.dto.socket.SignalingMessage;
import com.camrelay.exception.WebSocketAuthenticationException;
import com.camrelay.service.interfaces.SignalingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages websocket sessions and routes signaling messages between users.
 * Stores sessions by username. Provides simple 1:1 routing.
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalingServiceImpl implements SignalingService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a session for a username.
     * @param username username
     * @param session websocket session
     * @since 1.0
     */

    public void register(String username, WebSocketSession session) {
        if (sessions.size() >= 2) {
            log.warn("Connection rejected for {} — max users reached", username);
            throw new WebSocketAuthenticationException("Maximum number of WebSocket users reached");
        }

        sessions.put(username, session);
        log.info("Registered websocket session for user {}", username);
        broadcastUserList();
    }

    /**
     * Removes session for username.
     * @param username username
     * @since 1.0
     */
    public void unregister(String username) {
        sessions.remove(username);
        log.info("Unregistered websocket session for user {}", username);
        broadcastUserList();
    }

    /**
     * Routes a signaling message to the target user (if online).
     * @param msg message
     */
    public void route(SignalingMessage msg) {
        try {
            if (msg.getTo() == null) {
                log.warn("Dropping message without 'to' field from {}", msg.getFrom());
                return;
            }
            WebSocketSession target = sessions.get(msg.getTo());
            if (target == null || !target.isOpen()) {
                log.warn("Target {} not connected — cannot deliver message from {}", msg.getTo(), msg.getFrom());
                return;
            }
            String json = mapper.writeValueAsString(msg);
            target.sendMessage(new TextMessage(json));
            log.debug("Routed {} from {} -> {}", msg.getType(), msg.getFrom(), msg.getTo());
        } catch (Exception e) {
            log.error("Failed to route signaling message: {}", e.getMessage(), e);
        }
    }

    /**
     * Counts how many connected websocket sessions belong to users with the RECEIVER role.
     * <p>A session is considered a "receiver" if its authenticated principal contains an
     * authority equal to "RECEIVER". Null principals or authorities are safely ignored.</p>
     * @return number of connected receiver sessions
     */
    public long countReceivers() {
        return sessions.values().stream()
                .filter(s -> {
                    Authentication auth = safeAuth(s);
                    if (auth == null) return false;

                    return auth.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_RECEIVER".equals(a.getAuthority()));
                })
                .count();
    }

    /**
     * Counts how many connected websocket sessions belong to users WITHOUT the RECEIVER role.
     * <p>Sessions with null principal or null authorities are treated as non-receivers.</p>
     * @return number of connected non-receiver sessions
     */
    public long countNonReceivers() {
        return sessions.values().stream()
                .filter(s -> {
                    Authentication auth = safeAuth(s);
                    if (auth == null) return true;

                    return auth.getAuthorities().stream()
                            .noneMatch(a -> "ROLE_RECEIVER".equals(a.getAuthority()));
                })
                .count();
    }

    /**
     * Safely extracts an Authentication object from a WebSocketSession's principal.
     *
     * @param session the websocket session
     * @return Authentication or null if unavailable or wrong type
     */
    private Authentication safeAuth(WebSocketSession session) {
        if (session == null) return null;
        if (session.getPrincipal() instanceof Authentication auth) {
            return auth;
        }
        return null;
    }

    private void broadcastUserList() {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "user-list");

            ArrayNode arr = mapper.createArrayNode();
            for (String u : sessions.keySet()) {
                arr.add(u);
            }
            root.set("payload", arr);

            String json = mapper.writeValueAsString(root);

            for (Map.Entry<String, WebSocketSession> e : sessions.entrySet()) {
                WebSocketSession s = e.getValue();
                if (s != null && s.isOpen()) {
                    s.sendMessage(new TextMessage(json));
                }
            }
            log.debug("Broadcasted user-list: {}", json);
        } catch (Exception ex) {
            log.error("Failed to broadcast user list: {}", ex.getMessage(), ex);
        }
    }
}
