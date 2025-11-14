package com.camrelay.component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Helper for adding / clearing / reading secure cookies.
 * @since 1.0
 */
@Component
public class CookieComponent {
    /**
     * Adds a secure HTTP cookie to the response.
     * @param response the {@link HttpServletResponse} to which the cookie will be added
     * @param name the name of the cookie
     * @param value the value of the cookie
     * @param maxAgeMs the maximum age of the cookie in milliseconds
     * @since 1.0
     */
    public void addCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                //.domain("localhost")
                .maxAge(maxAgeMs / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Clears a secure HTTP cookie to the response.
     * @param response the {@link HttpServletResponse} to which the cookie will be added
     * @param name the name of the cookie
     * @since 1.0
     */
    public void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                //.domain("localhost")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Retrieves the value of a cookie by its name.
     * @param request the incoming HTTP request
     * @param name cookie name
     * @return cookie value or null if not present
     * @since 1.0
     */
    public String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) return cookie.getValue();
        }
        return null;
    }
}
