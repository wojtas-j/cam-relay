package com.camrelay.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for JWT authentication in the Cam Relay application.
 * <p>Properties include:</p>
 * <ul>
 *     <li>{@link #secret} - the secret key used to sign JWT tokens</li>
 *     <li>{@link #expirationMs} - the expiration time for access tokens in milliseconds</li>
 *     <li>{@link #refreshExpirationDays} - the expiration time for refresh tokens in days</li>
 * </ul>
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long expirationMs;
    private long refreshExpirationDays;

    /**
     * Validates JWT properties after initialization.
     * @throws IllegalArgumentException if properties are invalid
     * @since 1.0
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT access token expirationMs must be positive");
        }
        if (refreshExpirationDays <= 0) {
            throw new IllegalArgumentException("JWT refresh token expirationDays must be positive");
        }
    }
}
