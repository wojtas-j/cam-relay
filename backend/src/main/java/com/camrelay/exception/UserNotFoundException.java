package com.camrelay.exception;

/**
 * Exception thrown when a user is not found in the system.
 * Extends {@link AuthenticationException} to integrate with the application's authentication error handling.
 * @since 1.0
 */
public class UserNotFoundException extends AuthenticationException {

    /**
     * Constructs a new {@code UserNotFoundException} with the specified detail message.
     * @param message the detail message
     * @since 1.0
     */
    public UserNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code UserNotFoundException} with the specified detail message and cause.
     * @param message the detail message
     * @param cause the cause of the exception
     * @since 1.0
     */
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
