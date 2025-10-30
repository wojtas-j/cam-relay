package com.camrelay.exception;

/**
 * Exception thrown when a user attempts to register with a username that is already in use.
 * Extends {@link AuthenticationException} to integrate with the application's authentication error handling.
 * @since 1.0
 */
public class UserAlreadyExistsException extends AuthenticationException {

    /**
     * Constructs a new {@code UserAlreadyExistsException} with the specified detail message.
     * @param message the detail message explaining the reason for the exception
     * @since 1.0
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code UserAlreadyExistsException} with the specified detail message and cause.
     * @param message the detail message explaining the reason for the exception
     * @param cause   the cause of the exception (a {@code null} value is permitted, and indicates that the cause is nonexistent or unknown)
     * @since 1.0
     */
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
