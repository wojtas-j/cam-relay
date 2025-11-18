package com.camrelay.configuration;

import com.camrelay.service.JwtTokenProviderImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures Spring Security for the Cam Relay application, enabling JWT-based authentication.
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProviderImpl jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtTokenProviderImpl jwtTokenProvider, @Lazy UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Configures the security filter chain to protect endpoints and enable JWT authentication.
     * <p>
     * Security rules:
     * <ul>
     *     <li>Public access to <code>/api/auth/login</code>.</li>
     *     <li>Public access to <code>/api/auth/refresh</code> for token refresh.</li>
     *     <li>Admin-only access to <code>/api/admin/**</code>.</li>
     *     <li>Public access to <code>/actuator/health</code>.</li>
     *     <li>Admin-only access to <code>/actuator/**</code>.</li>
     * </ul>
     * <p>
     * Security behavior:
     * <ul>
     *     <li>Disables CSRF, form login, and HTTP basic authentication (JWT-based auth is used).</li>
     *     <li>Stateless session management.</li>
     *     <li>Registers a JWT authentication filter before <code>UsernamePasswordAuthenticationFilter</code>.</li>
     *     <li>Returns a JSON response with structured problem details for access denied (HTTP 403) errors.</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} object used to configure web security
     * @return a {@link SecurityFilterChain} defining the security configuration
     * @throws Exception if an error occurs during configuration
     * @since 1.0
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("""
                    {
                        "type": "/problems/authentication-failed",
                        "title": "Authentication Failed",
                        "status": 401,
                        "detail": "You must provide a valid token to access this resource"
                    }
                    """);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("""
                    {
                        "type": "/problems/access-denied",
                        "title": "Access Denied",
                        "status": 403,
                        "detail": "You do not have permission to access this resource"
                    }
                    """);
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Provides the JWT authentication filter for validating JWT tokens.
     * @return a JwtAuthenticationFilter instance
     * @since 1.0
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    /**
     * Configures CORS to allow requests from the frontend.
     * @return a CorsConfigurationSource instance
     * @since 1.0
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("https://localhost:3000", "https://192.168.100.3:3000", "https://87.205.113.203:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
