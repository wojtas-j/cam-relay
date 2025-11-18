package com.camrelay.controller;

import com.camrelay.component.CookieComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.properties.JwtProperties;
import com.camrelay.configuration.SecurityConfig;
import com.camrelay.dto.user.LoginRequest;
import com.camrelay.entity.RefreshTokenEntity;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.GlobalExceptionHandler;
import com.camrelay.service.interfaces.AuthenticationService;
import com.camrelay.service.JwtTokenProviderImpl;
import com.camrelay.service.interfaces.RefreshTokenService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests the functionality of {@link AuthenticationController} in the Cam Relay application.
 * @since 1.0
 */
@WebMvcTest(AuthenticationController.class)
@ContextConfiguration(classes = {AuthenticationController.class, GlobalExceptionHandler.class, SecurityConfig.class, CookieComponent.class})
@Import(SecurityConfig.class)
class AuthenticationControllerTests {

    private static final String AUTH_URL = "/api/auth";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "Password123!";
    private static final String TEST_TOKEN = "jwt-token";
    private static final String TEST_REFRESH_TOKEN = "refresh-token";
    private static final String INVALID_TOKEN = "invalid-token";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("unused")
    @MockitoBean
    private AuthenticationService authenticationService;

    @SuppressWarnings("unused")
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @SuppressWarnings("unused")
    @MockitoBean
    private JwtTokenProviderImpl jwtTokenProvider;

    @SuppressWarnings("unused")
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @SuppressWarnings("unused")
    @MockitoBean
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private UserEntity userEntity;
    private Authentication authentication;

    /**
     * Sets up the test environment with mocked dependencies.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        userEntity = UserEntity.builder()
                .username(TEST_USERNAME)
                .password("encodedPassword")
                .roles(Set.of(Role.USER))
                .build();

        User userDetails = new User(TEST_USERNAME, "encodedPassword", Collections.emptyList());
        authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Nested
    class GetCurrentUserTests {
        /**
         * Tests retrieving current user information successfully.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldGetCurrentUserSuccessfully() throws Exception {
            // Arrange
            UserResponse userResponse = new UserResponse(TEST_USERNAME, Set.of(Role.USER), LocalDateTime.now());
            when(authenticationService.findByUsername(TEST_USERNAME)).thenReturn(userResponse);

            // Act & Assert
            mockMvc.perform(get(AUTH_URL + "/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                    .andExpect(jsonPath("$.roles", hasSize(1)))
                    .andExpect(jsonPath("$.roles[0]").value("USER"));

            // Verify
            verify(authenticationService).findByUsername(TEST_USERNAME);
        }

        /**
         * Tests rejecting get current user when user is not authenticated.
         * @since 1.0
         */
        @Test
        void shouldRejectGetCurrentUserWhenNotAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(get(AUTH_URL + "/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401));

            // Verify
            verify(authenticationService, never()).findByUsername(any());
        }

        /**
         * Tests rejecting get current user when user is not found.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldRejectGetCurrentUserWhenUserNotFound() throws Exception {
            // Arrange
            when(authenticationService.findByUsername(TEST_USERNAME))
                    .thenThrow(new AuthenticationException("User not found: " + TEST_USERNAME));

            // Act & Assert
            mockMvc.perform(get(AUTH_URL + "/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("User not found: " + TEST_USERNAME));

            // Verify
            verify(authenticationService).findByUsername(TEST_USERNAME);
        }
    }

    @Nested
    class LoginTests {
        /**
         * Tests successful user login and cookie creation.
         * Ensures that accessToken and refreshToken cookies are set.
         * @since 1.0
         */
        @Test
        void shouldLoginUserSuccessfully() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(jwtTokenProvider.generateToken(authentication)).thenReturn(TEST_TOKEN);
            when(authenticationService.findEntityByUsername(TEST_USERNAME)).thenReturn(userEntity);
            when(refreshTokenService.generateRefreshToken(userEntity))
                    .thenReturn(createRefreshTokenEntity(TEST_REFRESH_TOKEN, userEntity));

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("accessToken=" + TEST_TOKEN))))
                    .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refreshToken=" + TEST_REFRESH_TOKEN))))
                    .andExpect(content().string(""));

            // Verify
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(jwtTokenProvider).generateToken(authentication);
            verify(authenticationService).findEntityByUsername(TEST_USERNAME);
            verify(refreshTokenService).generateRefreshToken(userEntity);
        }

        /**
         * Tests rejecting login with invalid credentials.
         * @since 1.0
         */
        @Test
        void shouldRejectLoginWithInvalidCredentials() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest(TEST_USERNAME, "wrongpassword");
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Invalid username or password"));

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));

            // Verify
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(jwtTokenProvider, never()).generateToken(any());
            verify(authenticationService, never()).findByUsername(any());
            verify(refreshTokenService, never()).generateRefreshToken(any());
        }

        /**
         * Tests rejecting login with empty username.
         * @since 1.0
         */
        @Test
        void shouldRejectLoginWithEmptyUsername() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest("", TEST_PASSWORD);

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.title").value("Validation Error"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(containsString("username Username cannot be blank")));

            // Verify
            verify(authenticationManager, never()).authenticate(any());
            verify(jwtTokenProvider, never()).generateToken(any());
            verify(authenticationService, never()).findByUsername(any());
            verify(refreshTokenService, never()).generateRefreshToken(any());
        }
    }

    @Nested
    class RefreshTokenTests {
        /**
         * Tests successful token refresh and cookie rotation.
         * Verifies that new access and refresh cookies are returned.
         * @since 1.0
         */
        @Test
        void shouldRefreshTokenSuccessfully() throws Exception {
            // Arrange
            Cookie refreshCookie = new Cookie(REFRESH_COOKIE_NAME, TEST_REFRESH_TOKEN);

            RefreshTokenEntity oldToken = createRefreshTokenEntity(TEST_REFRESH_TOKEN, userEntity);
            RefreshTokenEntity newToken = createRefreshTokenEntity("new-refresh-token", userEntity);

            when(refreshTokenService.validateRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(oldToken);
            when(refreshTokenService.generateRefreshToken(userEntity)).thenReturn(newToken);
            when(jwtTokenProvider.generateToken(any(Authentication.class))).thenReturn("new-access-token");

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/refresh")
                            .cookie(refreshCookie))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andExpect(header().stringValues("Set-Cookie", hasItems(
                            containsString("accessToken=new-access-token"),
                            containsString("refreshToken=new-refresh-token")
                    )));

            // Verify
            verify(refreshTokenService).validateRefreshToken(TEST_REFRESH_TOKEN);
            verify(refreshTokenService).deleteByUser(userEntity);
            verify(refreshTokenService).generateRefreshToken(userEntity);
            verify(jwtTokenProvider).generateToken(any(Authentication.class));
        }

        /**
         * Tests rejecting refresh request when no refreshToken cookie is present.
         * @since 1.0
         */
        @Test
        void shouldRejectRefreshWhenNoRefreshTokenCookie() throws Exception {
            // Arrange
            // no cookies

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Missing refresh token"));

            // Verify
            verify(refreshTokenService, never()).validateRefreshToken(any());
        }

        /**
         * Tests rejecting token refresh when the refresh token is invalid.
         * @since 1.0
         */
        @Test
        void shouldRejectRefreshWithInvalidToken() throws Exception {
            // Arrange
            Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, INVALID_TOKEN);

            when(refreshTokenService.validateRefreshToken(INVALID_TOKEN))
                    .thenThrow(new AuthenticationException("Invalid refresh token"));

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/refresh")
                            .cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.detail").value("Invalid refresh token"));

            // Verify
            verify(refreshTokenService).validateRefreshToken(INVALID_TOKEN);
            verify(refreshTokenService, never()).deleteByUser(any());
            verify(refreshTokenService, never()).generateRefreshToken(any());
            verify(jwtTokenProvider, never()).generateToken(any());
        }

        /**
         * Tests rate limiting for the refresh endpoint.
         * @since 1.0
         */
        @Test
        void shouldRejectRefreshWhenRateLimitExceeded() throws Exception {
            // Arrange
            Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, TEST_REFRESH_TOKEN);

            RateLimiter rateLimiter = mock(RateLimiter.class);
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.ofDefaults();

            when(rateLimiter.getName()).thenReturn("refreshToken");
            when(rateLimiter.getRateLimiterConfig()).thenReturn(rateLimiterConfig);

            RequestNotPermitted exception =
                    RequestNotPermitted.createRequestNotPermitted(rateLimiter);

            when(refreshTokenService.validateRefreshToken(TEST_REFRESH_TOKEN)).thenThrow(exception);

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/refresh")
                            .cookie(cookie))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.type").value("/problems/rate-limit-exceeded"))
                    .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.detail").value(containsString("Too many requests")))
                    .andExpect(jsonPath("$.instance").value(AUTH_URL + "/refresh"));

            // Verify
            verify(refreshTokenService).validateRefreshToken(TEST_REFRESH_TOKEN);
            verify(refreshTokenService, never()).deleteByUser(any());
            verify(refreshTokenService, never()).generateRefreshToken(any());
            verify(jwtTokenProvider, never()).generateToken(any());
        }
    }

    @Nested
    class LogoutTests {
        /**
         * Tests successful user logout.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldLogoutUserSuccessfully() throws Exception {
            // Arrange
            when(authenticationService.findEntityByUsername(TEST_USERNAME)).thenReturn(userEntity);
            doNothing().when(refreshTokenService).deleteByUser(userEntity);

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // Verify
            verify(authenticationService).findEntityByUsername(TEST_USERNAME);
            verify(refreshTokenService).deleteByUser(userEntity);
        }

        /**
         * Tests rejecting logout when user is not found.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldRejectLogoutWhenUserNotFound() throws Exception {
            // Arrange
            when(authenticationService.findEntityByUsername(TEST_USERNAME))
                    .thenThrow(new AuthenticationException("User not found: " + TEST_USERNAME));

            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("User not found: " + TEST_USERNAME));

            // Verify
            verify(authenticationService).findEntityByUsername(TEST_USERNAME);
            verify(refreshTokenService, never()).deleteByUser(any());
        }

        /**
         * Tests rejecting logout when not authenticated.
         * @since 1.0
         */
        @Test
        void shouldRejectLogoutWhenNotAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(post(AUTH_URL + "/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").exists());

            // Verify
            verify(authenticationService, never()).findByUsername(any());
            verify(refreshTokenService, never()).deleteByUser(any());
        }
    }

    /**
     * Creates a mock RefreshTokenEntity for testing purposes.
     * @param token the refresh token
     * @param user the UserEntity object
     * @return a RefreshTokenEntity with the specified token and user
     * @since 1.0
     */
    private RefreshTokenEntity createRefreshTokenEntity(String token, UserEntity user) {
        return RefreshTokenEntity.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
    }
}
