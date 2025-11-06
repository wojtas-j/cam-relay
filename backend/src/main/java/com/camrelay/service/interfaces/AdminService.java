package com.camrelay.service.interfaces;

import com.camrelay.dto.user.AdminGetUsersResponse;
import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.exception.AuthenticationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for admin-related operations in the Cam Relay application.
 * @since 1.0
 */
public interface AdminService {

    /**
     * Retrieves a paginated list of all users.
     * @param pageable pagination information (page number, size)
     * @return a {@link Page} of {@link AdminGetUsersResponse} objects
     * @since 1.0
     */
    Page<AdminGetUsersResponse> getAllUsers(Pageable pageable);

    /**
     * Create a new user with the provided details.
     * @param request the registration request containing username, and password
     * @return the created UserResponse.
     * @throws AuthenticationException if the username or email is already taken
     * @since 1.0
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Deletes a user and their associated refresh tokens.
     * @param id the ID of the user to delete
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    void deleteUser(Long id);

    /**
     * Deletes all refresh tokens for a specific user.
     * @param id the ID of the user whose tokens are to be deleted
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    void deleteRefreshTokens(Long id);
}
