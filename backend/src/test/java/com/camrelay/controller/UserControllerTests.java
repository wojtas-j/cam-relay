package com.camrelay.controller;

import com.camrelay.component.CookieComponent;
import com.camrelay.configuration.SecurityConfig;
import com.camrelay.dto.user.GetUsersByRoleResponse;
import com.camrelay.entity.Role;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.GlobalExceptionHandler;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.properties.CorsProperties;
import com.camrelay.service.JwtTokenProviderImpl;
import com.camrelay.service.interfaces.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the functionality of {@link UserController} in the Cam Relay application.
 * @since 1.0
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@ContextConfiguration(classes = {UserController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@Import(SecurityConfig.class)
class UserControllerTests {

    private static final String TEST_USERNAME = "testuser";

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("unused")
    @MockitoBean
    private UserService userService;

    @SuppressWarnings("unused")
    @MockitoBean
    private JwtTokenProviderImpl jwtTokenProvider;

    @SuppressWarnings("unused")
    @MockitoBean
    private CookieComponent cookieComponent;

    @SuppressWarnings("unused")
    @MockitoBean
    private CorsProperties corsProperties;

    @Nested
    class DeleteUser {
        private static final String USERS_URL = "/api/users";
        /**
         * Tests deleting the user's account successfully.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldDeleteAccountSuccessfully() throws Exception {
            // Arrange
            doNothing().when(userService).deleteAccount(TEST_USERNAME);

            // Act & Assert
            mockMvc.perform(delete(USERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            // Verify
            verify(userService).deleteAccount(TEST_USERNAME);
        }

        /**
         * Tests rejecting account deletion when user is not found.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldRejectDeleteAccountWhenUserNotFound() throws Exception {
            // Arrange
            doThrow(new AuthenticationException("User not found: " + TEST_USERNAME))
                    .when(userService).deleteAccount(TEST_USERNAME);

            // Act & Assert
            mockMvc.perform(delete(USERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("User not found: " + TEST_USERNAME));

            // Verify
            verify(userService).deleteAccount(TEST_USERNAME);
        }

        /**
         * Tests rejecting account deletion when not authenticated.
         * @since 1.0
         */
        @Test
        void shouldRejectDeleteAccountWhenNotAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(delete(USERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").exists());

            // Verify
            verify(userService, never()).deleteAccount(any());
        }
    }

    @Nested
    class GetUsersByRole {
        private static final String RECEIVERS_URL = "/api/users/receivers";
        /**
         * Tests fetching all receivers successfully.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldReturnAllReceiversSuccessfully() throws Exception {
            List<GetUsersByRoleResponse> receivers = List.of(
                    new GetUsersByRoleResponse("receiver1"),
                    new GetUsersByRoleResponse("receiver2")
            );

            when(userService.getUsersByRole(Role.RECEIVER)).thenReturn(receivers);

            mockMvc.perform(MockMvcRequestBuilders.get(RECEIVERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").value("receiver1"))
                    .andExpect(jsonPath("$[1].username").value("receiver2"));

            verify(userService).getUsersByRole(Role.RECEIVER);
        }

        /**
         * Tests returning 404 when no receivers are found.
         * @since 1.0
         */
        @Test
        @WithMockUser(username = TEST_USERNAME)
        void shouldReturnNotFoundWhenNoReceiversExist() throws Exception {
            doThrow(new UserNotFoundException("No users found with role: RECEIVER"))
                    .when(userService).getUsersByRole(Role.RECEIVER);

            mockMvc.perform(MockMvcRequestBuilders.get(RECEIVERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("No users found with role: RECEIVER"))
                    .andExpect(jsonPath("$.type").value("/problems/user-not-found"))
                    .andExpect(jsonPath("$.title").value("User Not Found"))
                    .andExpect(jsonPath("$.status").value(404));


            verify(userService).getUsersByRole(Role.RECEIVER);
        }

        /**
         * Tests rejecting request when user is not authenticated.
         * @since 1.0
         */
        @Test
        void shouldRejectGetReceiversWhenNotAuthenticated() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get(RECEIVERS_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verify(userService, never()).getUsersByRole(any());
        }
    }
}

