package com.camrelay.service;

import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.repository.UserRepository;
import com.camrelay.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the functionality of {@link UserServiceImpl} in the Cam Relay application.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class UserServiceImplTests {
    private static final String TEST_USERNAME = "testuser";
    private static final String ENCODED_PASSWORD = "encodedPassword";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private UserEntity userEntity;

    /**
     * Sets up the test environment with mocked dependencies.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        userEntity = UserEntity.builder()
                .username(TEST_USERNAME)
                .password(ENCODED_PASSWORD)
                .roles(Set.of(Role.USER))
                .build();
    }

    @Nested
    class DeleteAccountTests {
        /**
         * Tests deleting the user's account successfully.
         * @since 1.0
         */
        @Test
        void shouldDeleteAccountSuccessfully() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(userEntity));
            doNothing().when(refreshTokenService).deleteByUser(userEntity);
            doNothing().when(userRepository).delete(userEntity);

            // Act
            userService.deleteAccount(TEST_USERNAME);

            // Assert
            verify(userRepository).findByUsername(TEST_USERNAME);
            verify(refreshTokenService).deleteByUser(userEntity);
            verify(userRepository).delete(userEntity);
        }

        /**
         * Tests throwing AuthenticationException when user is not found during account deletion.
         * @since 1.0
         */
        @Test
        void shouldThrowAuthenticationExceptionForNonExistentUser() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

            // Act & Assert
            AuthenticationException exception = assertThrows(AuthenticationException.class,
                    () -> userService.deleteAccount(TEST_USERNAME));
            assertThat(exception.getMessage()).isEqualTo("User not found with username: " + TEST_USERNAME);

            // Verify
            verify(userRepository).findByUsername(TEST_USERNAME);
            verify(refreshTokenService, never()).deleteByUser(any());
            verify(userRepository, never()).delete(any());
        }
    }

    @Nested
    class FindByUsernameTests {
        /**
         * Tests finding a user by username successfully.
         * @since 1.0
         */
        @Test
        void shouldFindByUsernameSuccessfully() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(userEntity));

            // Act
            UserEntity result = userService.findByUsername(TEST_USERNAME);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(result.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(result.getRoles()).hasSize(1).contains(Role.USER);
            verify(userRepository).findByUsername(TEST_USERNAME);
        }

        /**
         * Tests throwing AuthenticationException when user is not found by username.
         * @since 1.0
         */
        @Test
        void shouldThrowAuthenticationExceptionForNonExistentUser() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

            // Act & Assert
            AuthenticationException exception = assertThrows(AuthenticationException.class,
                    () -> userService.findByUsername(TEST_USERNAME));
            assertThat(exception.getMessage()).isEqualTo("User not found with username: " + TEST_USERNAME);

            // Verify
            verify(userRepository).findByUsername(TEST_USERNAME);
        }
    }
}
