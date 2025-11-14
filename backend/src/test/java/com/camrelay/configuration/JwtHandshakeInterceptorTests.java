package com.camrelay.configuration;

import com.camrelay.service.JwtTokenProviderImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link JwtHandshakeInterceptor}, validating JWT cookie handling,
 * token validation logic and username extraction.
 * @since 1.0
 */
class JwtHandshakeInterceptorTests {

    private JwtTokenProviderImpl jwtProvider;
    private JwtHandshakeInterceptor interceptor;
    private WebSocketHandler webSocketHandler;

    private ServletServerHttpRequest servletRequest;
    private HttpServletRequest httpServletRequest;
    private ServerHttpResponse response;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtTokenProviderImpl.class);
        interceptor = new JwtHandshakeInterceptor(jwtProvider);

        webSocketHandler = mock(WebSocketHandler.class);
        httpServletRequest = mock(HttpServletRequest.class);

        servletRequest = mock(ServletServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);

        when(servletRequest.getServletRequest()).thenReturn(httpServletRequest);
    }

    /**
     * Tests rejecting handshake for non-servlet requests.
     * @since 1.0
     */
    @Test
    void beforeHandshake_nonServletRequest_rejected() throws Exception {
        ServerHttpRequest nonServletReq = mock(ServerHttpRequest.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(nonServletReq, response, webSocketHandler, attrs);

        assertFalse(result);
        assertTrue(attrs.isEmpty());
    }

    /**
     * Tests rejecting when no cookies are present.
     * @since 1.0
     */
    @Test
    void beforeHandshake_noCookies_rejected() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(null);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertFalse(result);
        assertTrue(attrs.isEmpty());
    }

    /**
     * Tests rejecting when accessToken cookie is missing.
     * @since 1.0
     */
    @Test
    void beforeHandshake_missingAccessToken_rejected() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(new Cookie[] {
                new Cookie("OTHER", "123")
        });

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertFalse(result);
    }

    /**
     * Tests rejecting when accessToken is blank.
     * @since 1.0
     */
    @Test
    void beforeHandshake_blankAccessToken_rejected() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(new Cookie[] {
                new Cookie("accessToken", "")
        });

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertFalse(result);
    }

    /**
     * Tests rejecting invalid tokens.
     * @since 1.0
     */
    @Test
    void beforeHandshake_invalidToken_rejected() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(
                new Cookie[]{ new Cookie("accessToken", "badtoken") }
        );

        when(jwtProvider.validateToken("badtoken")).thenReturn(false);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertFalse(result);
        verify(jwtProvider, times(1)).validateToken("badtoken");
    }

    /**
     * Tests rejecting when provider throws exception.
     * @since 1.0
     */
    @Test
    void beforeHandshake_providerThrows_rejected() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(
                new Cookie[]{ new Cookie("accessToken", "tkn") }
        );

        when(jwtProvider.validateToken("tkn")).thenThrow(new RuntimeException("fail"));

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertFalse(result);
    }

    /**
     * Tests accepting and extracting username for valid tokens.
     * @since 1.0
     */
    @Test
    void beforeHandshake_validToken_acceptsAndStoresUsername() throws Exception {
        when(httpServletRequest.getCookies()).thenReturn(
                new Cookie[]{ new Cookie("accessToken", "goodtoken") }
        );

        when(jwtProvider.validateToken("goodtoken")).thenReturn(true);
        when(jwtProvider.getUsernameFromToken("goodtoken")).thenReturn("alice");

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(servletRequest, response, webSocketHandler, attrs);

        assertTrue(result);
        assertEquals("alice", attrs.get("username"));

        verify(jwtProvider, times(1)).validateToken("goodtoken");
        verify(jwtProvider, times(1)).getUsernameFromToken("goodtoken");
    }
}
