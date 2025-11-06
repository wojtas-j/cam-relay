package com.camrelay.service.interfaces;

import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;

/**
 * Service interface for handling user-related operations in the Cam Relay application.
 * @since 1.0
 */
public interface UserService {

    /**
     * Deletes a user's account and associated refresh tokens.
     * @param username the username of the user
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    void deleteAccount(String username);

    /**
     * Finds a user by username.
     * @param username the username to search for
     * @return the UserEntity
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    UserEntity findByUsername(String username);
}
