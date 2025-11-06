package com.camrelay.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.camrelay.entity.UserEntity;

import java.util.Optional;

/**
 * Repository for performing CRUD operations on {@link UserEntity}.
 * @since 1.0
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    /**
     * Finds a user by username.
     * @param username the username to search for
     * @return an Optional containing the user if found
     * @since 1.0
     */
    Optional<UserEntity> findByUsername(String username);
}
