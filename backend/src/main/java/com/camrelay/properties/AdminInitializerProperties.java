package com.camrelay.properties;

import com.camrelay.entity.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configuration properties for admin initialization to database in the Cam Relay application.
 * <p>Properties include:</p>
 * <ul>
 *     <li>{@link #username} - the username</li>
 *     <li>{@link #password} - the user password</li>
 *     <li>{@link #roles} - the user roles</li>
 * </ul>
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "initializer")
@Getter
@Setter
public class AdminInitializerProperties {
    private String username;
    private String password;
    private Set<Role> roles;
}
