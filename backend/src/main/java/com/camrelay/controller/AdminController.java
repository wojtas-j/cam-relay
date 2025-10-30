package com.camrelay.controller;

import com.camrelay.dto.user.AdminGetUsersResponse;
import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.service.interfaces.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles HTTP requests for admin-related operations in the Cam Relay application.
 * @since 1.0
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    private final AdminService adminService;

    /**
     * Retrieves a paginated list of all users, excluding sensitive fields like password and API key
     * @param pageable pagination information (page number, size)
     * @return a page of user DTOs
     * @since 1.0
     */
    @Operation(summary = "Gets all users with pagination (admin-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users fetched successfully", content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<Page<AdminGetUsersResponse>> getAllUsers(@PageableDefault(size = 20) Pageable pageable) {
        log.info("Fetching all users for admin request");
        Page<AdminGetUsersResponse> users = adminService.getAllUsers(pageable);
        log.info("Successfully retrieved {} total users", users.getTotalElements());
        return ResponseEntity.ok(users);
    }

    /**
     * Create a new user with the provided details.
     * @param request the creation request with username and password.
     * @return ResponseEntity with the created user's details
     * @throws AuthenticationException if creation fails (e.g., username)
     * @since 1.0
     */
    @Operation(summary = "Create a new user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "409", description = "Username already taken", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/create")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating new user: {}", request.username());
        UserResponse user = adminService.createUser(request);
        log.info("User created successfully: {}", user.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.username(), user.roles(), user.createdAt()));
    }

    /**
     * Deletes a user and their associated refresh tokens.
     * @param id the ID of the user to delete
     * @return no content (204)
     * @since 1.0
     */
    @Operation(summary = "Deletes a user and their refresh tokens (admin-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with ID: {}", id);
        adminService.deleteUser(id);
        log.info("User deleted successfully with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes all refresh tokens for a specific user.
     * @param id the ID of the user whose tokens are to be deleted
     * @return no content (204)
     * @since 1.0
     */
    @Operation(summary = "Deletes refresh tokens for a user (admin-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh tokens deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/refresh-tokens/{id}")
    public ResponseEntity<Void> deleteRefreshTokens(@PathVariable Long id) {
        log.info("Deleting refresh tokens for user with ID: {}", id);
        adminService.deleteRefreshTokens(id);
        log.info("Refresh tokens deleted successfully for user with ID: {}", id);
        return ResponseEntity.noContent().build();
    }
}
