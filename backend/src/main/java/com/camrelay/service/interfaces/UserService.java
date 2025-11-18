package com.camrelay.service.interfaces;

import com.camrelay.dto.user.GetUsersByRoleResponse;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.UserNotFoundException;

import java.util.List;

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

    /**
     * Finds all users by role.
     * @param role the role to serach users with
     * @return the GetUsersByRoleResponse
     * @throws UserNotFoundException if user with that role does not exist
     * @since 1.0
     */
    List<GetUsersByRoleResponse> getUsersByRole(Role role);
}
