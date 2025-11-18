package com.camrelay.controller;

import com.camrelay.component.CookieComponent;
import com.camrelay.dto.user.GetUsersByRoleResponse;
import com.camrelay.entity.Role;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.service.interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Handles HTTP requests for user-related operations in the Cam Relay application.
 * @since 1.0
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserService userService;
    private final CookieComponent cookieComponent;

    /**
     * Retrieves a list of all users for containing role, excluding sensitive fields like password and API key
     * @return a list of user DTOs
     * @throws UserNotFoundException if user with that role does not exist
     * @since 1.0
     */
    @Operation(summary = "Gets all users with role RECEIVER (admin-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    @GetMapping("/receivers")
    public ResponseEntity<List<GetUsersByRoleResponse>> getAllReceivers() {
        log.info("Fetching all users with role RECEIVER");
        List<GetUsersByRoleResponse> receivers = userService.getUsersByRole(Role.RECEIVER);
        log.info("Found {} receivers", receivers.size());
        return ResponseEntity.ok(receivers);
    }

    /**
     * Deletes the authenticated user's account and associated refresh tokens.
     * @param userDetails the authenticated user's details
     * @return no content (204)
     * @since 1.0
     */
    @Operation(summary = "Deletes the authenticated user's account")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserDetails userDetails, HttpServletResponse response) {
        log.info("Deleting account for user: {}", userDetails.getUsername());
        userService.deleteAccount(userDetails.getUsername());

        cookieComponent.clearCookie(response, "accessToken");
        cookieComponent.clearCookie(response, "refreshToken");

        log.info("Account deleted successfully for user: {}", userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
