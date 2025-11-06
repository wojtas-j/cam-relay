package com.camrelay.dto.token;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for refresh token request in the Cam Relay application.
 * @param refreshToken the refresh token to validate
 * @since 1.0
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token cannot be blank")
        String refreshToken
) {
}
