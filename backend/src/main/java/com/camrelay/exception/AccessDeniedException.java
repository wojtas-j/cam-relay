package com.camrelay.exception;

/**
 * Exception thrown when a user attempts to access a resource without sufficient permissions.
 * Extends {@link AuthenticationException} to integrate with the application's authentication error handling.
 *
 * @since 1.0
 */
public class AccessDeniedException extends AuthenticationException {

  /**
   * Constructs a new {@code AccessDeniedException} with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   * @since 1.0
   */
  public AccessDeniedException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code AccessDeniedException} with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the exception
   * @param cause   the cause of the exception (a {@code null} value is permitted, and indicates that the cause is nonexistent or unknown)
   * @since 1.0
   */
  public AccessDeniedException(String message, Throwable cause) {
    super(message, cause);
  }
}
