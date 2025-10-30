package com.camrelay.repository;

import com.camrelay.entity.RefreshTokenEntity;
import com.camrelay.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing RefreshTokenEntity in the Cam Relay application.
 * @since 1.0
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Finds a refresh token by its token value.
     * @param token the refresh token value
     * @return an Optional containing the RefreshTokenEntity if found
     * @since 1.0
     */
    Optional<RefreshTokenEntity> findByToken(String token);

    /**
     * Finds all refresh tokens for a given user.
     * @param user the user whose tokens should be found
     * @return a List of RefreshTokenEntity objects
     * @since 1.0
     */
    List<RefreshTokenEntity> findByUser(UserEntity user);

    /**
     * Deletes all refresh tokens for a given user.
     * @param user the user whose tokens should be deleted
     * @since 1.0
     */
    void deleteByUser(UserEntity user);

    /**
     * Deletes all refresh tokens with an expiry date before the given time.
     *
     * @param expiryDate the cutoff date for token expiration
     * @since 1.0
     */
    void deleteByExpiryDateBefore(LocalDateTime expiryDate);
}
