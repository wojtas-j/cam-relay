package com.camrelay.controller;

import com.camrelay.component.CookieComponent;
import com.camrelay.dto.user.LoginRequest;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final CookieComponent cookieComponent;

    /**
     * Retrieves information about the currently authenticated user.
     *
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
     *
     * @param request the login request with username and password
     * @return {@link ResponseEntity} with the access and refresh tokens
     * @throws AuthenticationException if authentication fails
     * @since 1.0
     */
    @Operation(summary = "Authenticates a user and returns access and refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User authenticated successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid username or password", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Authenticating user: {}", request.username());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String accessToken = jwtTokenProvider.generateToken(authentication);
        UserEntity user = authenticationService.findEntityByUsername(request.username());
        RefreshTokenEntity refreshToken = refreshTokenService.generateRefreshToken(user);

        cookieComponent.addCookie(response, "accessToken", accessToken, jwtProperties.getExpirationMs());
        cookieComponent.addCookie(response, "refreshToken", refreshToken.getToken(), jwtProperties.getRefreshExpirationDays() * 86400000L);

        log.info("User authenticated successfully: {}", request.username());
        return ResponseEntity.ok().build();
    }

    /**
     * Refreshes an access token using a valid refresh token and rotates the refresh token.
     *
     * @param request the {@link HttpServletRequest} request containing the cookies with refresh token.
     * @return {@link ResponseEntity} with new access and refresh tokens
     * @throws AuthenticationException if the refresh token is invalid or expired
     * @since 1.0
     */
    @Operation(summary = "Refreshes an access token using a refresh token",
            description = "Use this endpoint to refresh an expired access token. Returns new access and refresh tokens along with expiration time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token refreshed successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests - rate limit exceeded", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @RateLimiter(name = "refreshToken")
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        log.info("Refreshing access token");
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshToken == null) {
            throw new AuthenticationException("Missing refresh token");
        }

        RefreshTokenEntity tokenEntity = refreshTokenService.validateRefreshToken(refreshToken);
        UserEntity user = tokenEntity.getUser();
        refreshTokenService.deleteByUser(user);
        RefreshTokenEntity newRefreshToken = refreshTokenService.generateRefreshToken(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        String newAccessToken = jwtTokenProvider.generateToken(authentication);

        cookieComponent.addCookie(response, "accessToken", newAccessToken, jwtProperties.getExpirationMs());
        cookieComponent.addCookie(response, "refreshToken", newRefreshToken.getToken(), jwtProperties.getRefreshExpirationDays() * 86400000L);

        log.info("Access token and refresh token rotated successfully for user: {}", user.getUsername());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Logs out the authenticated user by invalidating all their refresh tokens.
     *
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
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails, HttpServletResponse response) {
        log.info("Logging out user: {}", userDetails.getUsername());
        UserEntity user = authenticationService.findEntityByUsername(userDetails.getUsername());

        refreshTokenService.deleteByUser(user);
        cookieComponent.clearCookie(response, "accessToken");
        cookieComponent.clearCookie(response, "refreshToken");

        log.info("User logged out successfully: {}", user.getUsername());
        return ResponseEntity.ok().build();
    }
}
