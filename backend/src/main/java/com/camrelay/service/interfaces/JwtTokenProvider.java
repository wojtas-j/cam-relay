package com.camrelay.service.interfaces;

import com.camrelay.exception.AuthenticationException;
import org.springframework.security.core.Authentication;

/**
 * Interface for generating and validating JWT tokens in the Cam Relay application.
 * @since 1.0
 */
public interface JwtTokenProvider {

    /**
     * Generates a JWT token for the authenticated user.
     * @param authentication the authentication object containing user details
     * @return the generated JWT token
     * @since 1.0
     */
    String generateToken(Authentication authentication);

    /**
     * Extracts the username from a JWT token.
     * @param token the JWT token
     * @return the username extracted from the token
     * @throws AuthenticationException if the token is invalid or expired
     * @since 1.0
     */
    String getUsernameFromToken(String token);

    /**
     * Validates a JWT token.
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     * @since 1.0
     */
    boolean validateToken(String token);
}
