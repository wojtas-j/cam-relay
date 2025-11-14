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
}
