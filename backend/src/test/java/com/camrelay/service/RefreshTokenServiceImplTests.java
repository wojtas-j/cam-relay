package com.camrelay.service;

import com.camrelay.entity.RefreshTokenEntity;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.properties.JwtProperties;
import com.camrelay.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RefreshTokenServiceImpl} in the Cam Relay application.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class RefreshTokenServiceImplTests {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private UserEntity userEntity;
    private LocalDateTime now;

    /**
     * Sets up the test environment with mock UserEntity and LocalDateTime.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        userEntity = UserEntity.builder()
                .username("testuser")
                .password("encodedPassword")
                .roles(Set.of(Role.USER))
                .build();
        now = LocalDateTime.now();
    }

    /**
     * Tests successful generation of a refresh token.
     * @since 1.0
     */
    @Test
    void shouldGenerateRefreshTokenSuccessfully() {
        // Arrange
        when(jwtProperties.getRefreshExpirationDays()).thenReturn(7L);
        String token = UUID.randomUUID().toString();
        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(token)
                .user(userEntity)
                .expiryDate(now.plusDays(7))
                .build();
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenReturn(refreshToken);

        // Act
        RefreshTokenEntity result = refreshTokenService.generateRefreshToken(userEntity);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(token);
        assertThat(result.getUser()).isEqualTo(userEntity);
        assertThat(result.getExpiryDate()).isEqualTo(now.plusDays(7));
        verify(jwtProperties).getRefreshExpirationDays();
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    /**
     * Tests throwing IllegalArgumentException when refreshExpirationDays is invalid.
     * @since 1.0
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForInvalidExpirationDays() {
        // Arrange
        when(jwtProperties.getRefreshExpirationDays()).thenReturn(0L);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.generateRefreshToken(userEntity));
        assertThat(exception.getMessage()).contains("Refresh token expiration time must be positive");
        verify(jwtProperties).getRefreshExpirationDays();
        verify(refreshTokenRepository, never()).save(any());
    }

    /**
     * Tests successful validation of a refresh token.
     * @since 1.0
     */
    @Test
    void shouldValidateRefreshTokenSuccessfully() {
        // Arrange
        String token = UUID.randomUUID().toString();
        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(token)
                .user(userEntity)
                .expiryDate(now.plusDays(1))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(refreshToken));

        // Act
        RefreshTokenEntity result = refreshTokenService.validateRefreshToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(token);
        assertThat(result.getUser()).isEqualTo(userEntity);
        assertThat(result.getExpiryDate()).isEqualTo(now.plusDays(1));
        verify(refreshTokenRepository).findByToken(token);
        verify(refreshTokenRepository, never()).delete(any());
    }

    /**
     * Tests throwing AuthenticationException for an invalid refresh token.
     * @since 1.0
     */
    @Test
    void shouldThrowAuthenticationExceptionForInvalidRefreshToken() {
        // Arrange
        String token = UUID.randomUUID().toString();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> refreshTokenService.validateRefreshToken(token));
        assertThat(exception.getMessage()).contains("Invalid refresh token");
        verify(refreshTokenRepository).findByToken(token);
        verify(refreshTokenRepository, never()).delete(any());
    }

    /**
     * Tests throwing AuthenticationException for an expired refresh token and deleting it.
     * @since 1.0
     */
    @Test
    void shouldThrowAuthenticationExceptionForExpiredRefreshToken() {
        // Arrange
        String token = UUID.randomUUID().toString();
        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(token)
                .user(userEntity)
                .expiryDate(now.minusDays(1))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(refreshToken));
        doNothing().when(refreshTokenRepository).delete(refreshToken);

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> refreshTokenService.validateRefreshToken(token));
        assertThat(exception.getMessage()).contains("Refresh token expired");
        verify(refreshTokenRepository).findByToken(token);
        verify(refreshTokenRepository).delete(refreshToken);
    }

    /**
     * Tests successful deletion of refresh tokens for a user.
     * @since 1.0
     */
    @Test
    void shouldDeleteByUserSuccessfully() {
        // Arrange
        doNothing().when(refreshTokenRepository).deleteByUser(userEntity);

        // Act
        refreshTokenService.deleteByUser(userEntity);

        // Assert
        verify(refreshTokenRepository).deleteByUser(userEntity);
    }

    /**
     * Tests successful deletion of expired refresh tokens.
     * @since 1.0
     */
    @Test
    void shouldDeleteExpiredTokensSuccessfully() {
        // Arrange
        doNothing().when(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));

        // Act
        refreshTokenService.deleteExpiredTokens();

        // Assert
        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));
    }

    /**
     * Tests generating a refresh token when the maximum token limit is reached, ensuring the oldest token is deleted.
     * @since 1.0
     */
    @Test
    void shouldDeleteOldestTokenWhenMaxLimitReached() {
        // Arrange
        when(jwtProperties.getRefreshExpirationDays()).thenReturn(7L);

        List<RefreshTokenEntity> existingTokens = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RefreshTokenEntity token = RefreshTokenEntity.builder()
                    .token(UUID.randomUUID().toString())
                    .user(userEntity)
                    .createdAt(now.minusDays(5 - i))
                    .expiryDate(now.plusDays(7))
                    .build();
            existingTokens.add(token);
        }
        when(refreshTokenRepository.findByUser(userEntity)).thenReturn(existingTokens);

        // Mock deletion of the oldest token
        RefreshTokenEntity oldestToken = existingTokens.getFirst();
        doNothing().when(refreshTokenRepository).delete(oldestToken);

        // Mock saving the new token
        String newTokenValue = UUID.randomUUID().toString();
        RefreshTokenEntity newToken = RefreshTokenEntity.builder()
                .token(newTokenValue)
                .user(userEntity)
                .expiryDate(now.plusDays(7))
                .build();
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenReturn(newToken);

        // Act
        RefreshTokenEntity result = refreshTokenService.generateRefreshToken(userEntity);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(newTokenValue);
        assertThat(result.getUser()).isEqualTo(userEntity);
        assertThat(result.getExpiryDate()).isEqualTo(now.plusDays(7));
        verify(jwtProperties).getRefreshExpirationDays();
        verify(refreshTokenRepository).findByUser(userEntity);
        verify(refreshTokenRepository).delete(oldestToken);
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }
}
