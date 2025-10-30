package com.camrelay.exception;

/**
 * Exception thrown when an authentication error occurs in the Cam Relay application.
 * @since 1.0
 */
public class AuthenticationException extends RuntimeException {

    /**
     * Constructs a new AuthenticationException with the specified message.
     * @param message the error message describing the issue
     * @since 1.0
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs a new AuthenticationException with the specified message and cause.
     * @param message the error message describing the issue
     * @param cause the underlying cause of the error
     * @since 1.0
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
