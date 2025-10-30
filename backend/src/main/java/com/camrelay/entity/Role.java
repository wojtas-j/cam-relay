package com.camrelay.entity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

/**
 * Enum representing user roles in the Cam Relay application.
 * @since 1.0
 */
public enum Role {
    USER,
    RECEIVER,
    ADMIN;

    /**
     * Returns the set of authorities associated with the role.
     * @return a set of {@link SimpleGrantedAuthority} for Spring Security
     * @since 1.0
     */
    public Set<SimpleGrantedAuthority> getAuthorities() {
        return Set.of(new SimpleGrantedAuthority("ROLE_" + this.name()));
    }
}
