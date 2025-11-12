package com.camrelay.dto.user;

import com.camrelay.entity.Role;
import jakarta.validation.constraints.*;

import java.util.Set;

/**
 * DTO for user creation requests in the Cam Relay application.
 * @param username the username for the new user
 * @param password the password for the new user
 * @param roles roles for the new user
 * @since 1.0
 */
public record CreateUserRequest(
        @NotBlank(message = "Username cannot be blank")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores, and hyphens")
        String username,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
        String password,

        @NotEmpty(message = "At least one role must be provided")
        Set<Role> roles
) {}