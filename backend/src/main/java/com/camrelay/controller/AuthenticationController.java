package com.camrelay.controller;

import com.camrelay.dto.token.RefreshTokenRequest;
import com.camrelay.dto.user.LoginRequest;
import com.camrelay.dto.user.LoginResponse;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.properties.JwtProperties;
import com.camrelay.entity.RefreshTokenEntity;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.service.interfaces.AuthenticationService;
import com.camrelay.service.JwtTokenProviderImpl;
import com.camrelay.service.interfaces.RefreshTokenService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for handling authentication-related endpoints in the Cam Relay application.
 * @since 1.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProviderImpl jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    /**
     * Retrieves information about the currently authenticated user.
     * @param userDetails the authenticated user's details
     * @return {@link ResponseEntity} with the current user's info ({@link UserResponse}) - (username, roles, created time)
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    @Operation(summary = "Get current authenticated user's info", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user info", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no permission to access", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("Retrieving info for authenticated user: {}", userDetails.getUsername());
        UserResponse user = authenticationService.findByUsername(userDetails.getUsername());
        log.info("Successfully retrieved info for user: {}", user.username());
        return ResponseEntity.ok(new UserResponse(user.username(), user.roles(), user.createdAt()));
    }

    /**
     * Authenticates a user and returns access and refresh tokens.
     * @param request the login request with username and password
     * @return {@link ResponseEntity} with the access and refresh tokens
     * @throws AuthenticationException if authentication fails
     * @since 1.0
     */
    @Operation(summary = "Authenticates a user and returns access and refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User authenticated successfully", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid username or password", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Authenticating user: {}", request.username());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String accessToken = jwtTokenProvider.generateToken(authentication);
        UserEntity user = authenticationService.findEntityByUsername(request.username());
        RefreshTokenEntity refreshToken = refreshTokenService.generateRefreshToken(user);
        long expiresIn = jwtProperties.getExpirationMs() / 1000;
        log.info("User authenticated successfully: {}", request.username());
        return ResponseEntity.ok(new LoginResponse(accessToken, refreshToken.getToken(), expiresIn));
    }

    /**
     * Refreshes an access token using a valid refresh token and rotates the refresh token.
     * @param request the refresh token request containing the refresh token
     * @return {@link ResponseEntity} with new access and refresh tokens
     * @throws AuthenticationException if the refresh token is invalid or expired
     * @since 1.0
     */
    @Operation(summary = "Refreshes an access token using a refresh token",
            description = "Use this endpoint to refresh an expired access token. Returns new access and refresh tokens along with expiration time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token refreshed successfully", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests - rate limit exceeded", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @RateLimiter(name = "refreshToken")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refreshing access token");
        RefreshTokenEntity tokenEntity = refreshTokenService.validateRefreshToken(request.refreshToken());
        UserEntity user = tokenEntity.getUser();
        refreshTokenService.deleteByUser(user);
        RefreshTokenEntity newRefreshToken = refreshTokenService.generateRefreshToken(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        long expiresIn = jwtProperties.getExpirationMs() / 1000;
        log.info("Access token and refresh token rotated successfully for user: {}", user.getUsername());
        return ResponseEntity.ok(new LoginResponse(newAccessToken, newRefreshToken.getToken(), expiresIn));
    }

    /**
     * Logs out the authenticated user by invalidating all their refresh tokens.
     * @param userDetails the authenticated user's details
     * @return {@link ResponseEntity} indicating successful logout
     * @throws AuthenticationException if the user is not found
     * @since 1.0
     */
    @Operation(summary = "Logs out the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User logged out successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - no permission to access", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("Logging out user: {}", userDetails.getUsername());
        UserEntity user = authenticationService.findEntityByUsername(userDetails.getUsername());
        refreshTokenService.deleteByUser(user);
        log.info("User logged out successfully: {}", user.getUsername());
        return ResponseEntity.ok().build();
    }
}
