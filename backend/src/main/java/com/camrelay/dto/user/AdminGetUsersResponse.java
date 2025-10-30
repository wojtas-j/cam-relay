package com.camrelay.dto.user;

import com.camrelay.entity.Role;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for representing user details in the Cam Relay application.
 * @param id the user's id
 * @param username the user's username
 * @param roles the set of roles assigned to the user
 * @param createdAt the user's account created at time
 * @since 1.0
 */
public record AdminGetUsersResponse(Long id, String username, LocalDateTime createdAt, Set<Role> roles) {
}
