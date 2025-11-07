package com.camrelay.configuration;

import com.camrelay.service.JwtTokenProviderImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
 * Tests the functionality of {@link JwtAuthenticationFilter} when using JWT stored in cookies.
 * Verifies that authentication is correctly set or skipped depending on the cookie state.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    private static final String COOKIE_NAME = "accessToken";
    private static final String VALID_TOKEN = "valid-jwt-token";
    private static final String INVALID_TOKEN = "invalid-jwt-token";
    private static final String USERNAME = "testuser";

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
     * Clears the security context and initializes a mock user before each test.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userDetails = new User(USERNAME, "password", Collections.emptyList());
    }

    /**
     * Tests that the filter allows the request to proceed without authentication
     * when no cookies are present.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenNoCookies() throws ServletException, IOException {
        // Arrange
        when(request.getCookies()).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    /**
     * Tests that the filter proceeds without authentication when cookies exist
     * but the accessToken cookie is missing.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenAccessTokenCookieMissing() throws ServletException, IOException {
        // Arrange
        Cookie[] cookies = { new Cookie("otherCookie", "value") };
        when(request.getCookies()).thenReturn(cookies);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    /**
     * Tests that authentication is skipped when the access token is present in cookies
     * but the token is invalid.
     * @since 1.0
     */
    @Test
    void shouldProceedWithoutAuthenticationWhenTokenIsInvalid() throws ServletException, IOException {
        // Arrange
        Cookie[] cookies = { new Cookie(COOKIE_NAME, INVALID_TOKEN) };
        when(request.getCookies()).thenReturn(cookies);
        when(jwtTokenProvider.validateToken(INVALID_TOKEN)).thenReturn(false);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    /**
     * Tests that authentication is correctly set in the SecurityContext
     * when the JWT token from cookies is valid and the user exists.
     * @since 1.0
     */
    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws ServletException, IOException {
        // Arrange
        Cookie[] cookies = { new Cookie(COOKIE_NAME, VALID_TOKEN) };
        when(request.getCookies()).thenReturn(cookies);
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
     * Tests that the filter handles exceptions thrown during token validation
     * and proceeds without authentication.
     * @since 1.0
     */
    @Test
    void shouldHandleTokenValidationException() throws ServletException, IOException {
        // Arrange
        Cookie[] cookies = { new Cookie(COOKIE_NAME, INVALID_TOKEN) };
        when(request.getCookies()).thenReturn(cookies);
        when(jwtTokenProvider.validateToken(INVALID_TOKEN))
                .thenThrow(new RuntimeException("Token error"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    /**
     * Tests that the filter proceeds without authentication when a valid token is provided
     * but the user cannot be found.
     * @since 1.0
     */
    @Test
    void shouldHandleUsernameNotFoundException() throws ServletException, IOException {
        // Arrange
        Cookie[] cookies = { new Cookie(COOKIE_NAME, VALID_TOKEN) };
        when(request.getCookies()).thenReturn(cookies);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(VALID_TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenThrow(new UsernameNotFoundException("User not found"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUsernameFromToken(VALID_TOKEN);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
