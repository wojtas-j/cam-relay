package com.camrelay.configuration;

import com.camrelay.service.JwtTokenProviderImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the functionality of {@link JwtAuthenticationFilter} in the Cam Relay application.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    private static final String AUTH_HEADER = "Authorization";
    private static final String VALID_TOKEN = "valid-jwt-token";
    private static final String INVALID_TOKEN = "invalid-jwt-token";
    private static final String USERNAME = "testuser";
    private static final String BEARER_PREFIX = "Bearer ";

    @Mock
    private JwtTokenProviderImpl jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserDetails userDetails;

    /**
     * Sets up the test environment by clearing the SecurityContext and initializing UserDetails.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userDetails = new User(USERNAME, "password", Collections.emptyList());
    }

    /**
     * Tests that the filter proceeds without authentication when the Authorization header is null.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenAuthorizationHeaderIsNull() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    /**
     * Tests that the filter proceeds without authentication when the Authorization header does not start with 'Bearer '.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenAuthorizationHeaderDoesNotStartWithBearer() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn("InvalidToken");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    /**
     * Tests that the filter proceeds without authentication when the JWT token is invalid.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenTokenIsInvalid() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn(BEARER_PREFIX + INVALID_TOKEN);
        when(jwtTokenProvider.validateToken(INVALID_TOKEN)).thenReturn(false);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        verifyNoInteractions(userDetailsService);
    }

    /**
     * Tests that the filter sets authentication in SecurityContext when the JWT token is valid.
     * @since 1.0
     */
    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn(BEARER_PREFIX + VALID_TOKEN);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(VALID_TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUsernameFromToken(VALID_TOKEN);
        verify(userDetailsService).loadUserByUsername(USERNAME);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(USERNAME, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /**
     * Tests that the filter handles exceptions during token validation and proceeds without authentication.
     * @since 1.0
     */
    @Test
    void shouldHandleTokenValidationException() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn(BEARER_PREFIX + INVALID_TOKEN);
        when(jwtTokenProvider.validateToken(INVALID_TOKEN)).thenThrow(new RuntimeException("Invalid token"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    /**
     * Tests that the filter handles UsernameNotFoundException and proceeds without authentication.
     * @since 1.0
     */
    @Test
    void shouldHandleUsernameNotFoundException() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(AUTH_HEADER)).thenReturn(BEARER_PREFIX + VALID_TOKEN);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(VALID_TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenThrow(new UsernameNotFoundException("User not found"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUsernameFromToken(VALID_TOKEN);
        verify(userDetailsService).loadUserByUsername(USERNAME);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
