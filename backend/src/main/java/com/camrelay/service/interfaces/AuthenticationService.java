package com.camrelay.service.interfaces;

import com.camrelay.dto.user.UserResponse;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;

/**
 * Interface for handling user authentication in the Cam Relay application.
 * @since 1.0
 */
public interface AuthenticationService {

    /**
     * Finds a user by their username.
     * @param username the username of the user to find
     * @return the UserResponse corresponding to the given username
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    UserResponse findByUsername(String username);

    /**
     * Finds a user entity by their username for internal server operations (tokens, security).
     *
     * @param username the username of the user to find
     * @return the UserEntity
     * @throws AuthenticationException if the user is not found
     */
    UserEntity findEntityByUsername(String username);
}
