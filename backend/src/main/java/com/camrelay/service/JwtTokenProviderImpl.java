package com.camrelay.service;

import com.camrelay.properties.JwtProperties;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.service.interfaces.JwtTokenProvider;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Service implementation for generating and validating JWT tokens in the Cam Relay application.
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * Generates a JWT token for the authenticated user.
     * @param authentication the authentication object containing user details
     * @return the generated JWT token
     * @throws AuthenticationException if the authentication principal is invalid
     * @since 1.0
     */
    @Override
    public String generateToken(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (!(principal instanceof UserEntity user)) {
            log.error("Invalid principal type: {}", principal.getClass().getName());
            throw new AuthenticationException("Invalid authentication principal");
        }

        try {
            log.info("Generating JWT token for user: {}", user.getUsername());

            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + jwtProperties.getExpirationMs());

            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .claim("roles", user.getAuthorities())
                    .issuedAt(now)
                    .expiration(expiryDate)
                    .signWith(getSigningKey())
                    .compact();

            log.info("JWT token generated successfully for user: {}", user.getUsername());
            return token;
        } catch (JwtException e) {
            log.error("Failed to generate JWT token for user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new AuthenticationException("Failed to generate JWT token", e);
        }
    }

    /**
     * Extracts the username from a JWT token.
     * @param token the JWT token
     * @return the username extracted from the token
     * @throws AuthenticationException if the token is invalid or expired
     * @since 1.0
     */
    @Override
    public String getUsernameFromToken(String token) {
        log.info("Extracting username from JWT token");
        try {
            String username = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            log.info("Username extracted successfully: {}", username);
            return username;
        } catch (JwtException e) {
            log.error("Invalid or expired JWT token: {}", e.getMessage(), e);
            throw new AuthenticationException("Invalid or expired JWT token", e);
        }
    }

    /**
     * Validates a JWT token.
     * @param token the JWT token to validate
     * @return true if the token is valid
     * @throws AuthenticationException if the token is invalid or expired
     * @since 1.0
     */
    @Override
    public boolean validateToken(String token) {
        log.info("Validating JWT token");
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            log.info("JWT token validated successfully");
            return true;
        } catch (JwtException e) {
            log.error("Invalid or expired JWT token: {}", e.getMessage(), e);
            throw new AuthenticationException("Invalid or expired JWT token", e);
        } catch (IllegalArgumentException e) {
            log.error("JWT token is empty or null: {}", e.getMessage(), e);
            throw new AuthenticationException("JWT token is empty or null", e);
        }
    }

    /**
     * Retrieves the signing key from the JWT secret.
     * @return the secret key for signing JWT tokens
     * @since 1.0
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
