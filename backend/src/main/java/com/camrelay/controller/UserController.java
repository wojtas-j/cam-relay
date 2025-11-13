package com.camrelay.controller;

import com.camrelay.service.interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

        clearCookie(response, "accessToken");
        clearCookie(response, "refreshToken");

        log.info("Account deleted successfully for user: {}", userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears a secure HTTP cookie to the response.
     * @param response the {@link HttpServletResponse} to which the cookie will be added
     * @param name the name of the cookie
     * @since 1.0
     */
    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .domain("localhost")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
