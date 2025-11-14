package com.camrelay.service.interfaces;

import com.camrelay.dto.socket.SignalingMessage;
import org.springframework.web.socket.WebSocketSession;

public interface SignalingService {
    /**
     * Registers a session for a username.
     * @param username username
     * @param session websocket session
     * @since 1.0
     */
    void register(String username, WebSocketSession session);

    /**
     * Removes session for username.
     * @param username username
     * @since 1.0
     */
    void unregister(String username);

    /**
     * Routes a signaling message to the target user (if online).
     * @param msg message
     */
    void route(SignalingMessage msg);

    /**
     * Counts how many connected websocket sessions belong to users with the RECEIVER role.
     * <p>A session is considered a "receiver" if its authenticated principal contains an
     * authority equal to "RECEIVER". Null principals or authorities are safely ignored.</p>
     * @return number of connected receiver sessions
     */
    long countReceivers();

    /**
     * Counts how many connected websocket sessions belong to users WITHOUT the RECEIVER role.
     * <p>Sessions with null principal or null authorities are treated as non-receivers.</p>
     * @return number of connected non-receiver sessions
     */
    long countNonReceivers();
}
