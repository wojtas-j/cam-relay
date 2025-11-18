package com.camrelay.repository;

import com.camrelay.entity.Role;
import com.camrelay.exception.AuthenticationException;
import org.springframework.data.jpa.repository.JpaRepository;
import com.camrelay.entity.UserEntity;

import java.util.List;
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

    /**
     * Finds all users by role.
     * @param role the role to serach users with
     * @return the UserEntity
     * @since 1.0
     */
    List<UserEntity> findAllByRolesContaining(Role role);
}
