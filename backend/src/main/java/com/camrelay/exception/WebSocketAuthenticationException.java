package com.camrelay.exception;

/**
 * Thrown when WebSocket handshake authentication fails (invalid/missing JWT).
 * @since 1.0
 */
public class WebSocketAuthenticationException extends RuntimeException {
    public WebSocketAuthenticationException(String message) { super(message); }
}
