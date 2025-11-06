package com.camrelay.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for user login requests in the Cam Relay application.
 * @param username the username for login
 * @param password the password for login
 * @since 1.0
 */
public record LoginRequest(
        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Password cannot be blank")
        String password) {
}
