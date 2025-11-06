package com.camrelay.service;

import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationServiceImpl} in the Cam Relay application.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuthenticationServiceImplTests {

    private static final String TEST_USERNAME = "testuser";
    private static final String ENCODED_PASSWORD = "encodedPassword123";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private UserEntity userEntity;

    /**
     * Sets up the test environment with mock UserEntity.
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

    /**
     * Tests finding a user by username successfully.
     * @since 1.0
     */
    @Test
    void shouldFindByUsernameSuccessfully() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(userEntity));

        // Act
        UserEntity result = authenticationService.findEntityByUsername(TEST_USERNAME);

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
    void shouldThrowAuthenticationExceptionForNonExistentUsername() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authenticationService.findByUsername(TEST_USERNAME));
        assertThat(exception.getMessage()).isEqualTo("User not found: " + TEST_USERNAME);
        verify(userRepository).findByUsername(TEST_USERNAME);
    }

    /**
     * Tests loading user details by username successfully.
     * @since 1.0
     */
    @Test
    void shouldLoadUserByUsernameSuccessfully() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(userEntity));

        // Act
        UserDetails result = authenticationService.loadUserByUsername(TEST_USERNAME);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(result.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        verify(userRepository).findByUsername(TEST_USERNAME);
    }

    /**
     * Tests throwing UserNotFoundException when user is not found by username.
     * @since 1.0
     */
    @Test
    void shouldThrowUUserNotFoundExceptionForNonExistentUser() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> authenticationService.loadUserByUsername(TEST_USERNAME));
        assertThat(exception.getMessage()).isEqualTo("User not found with username: " + TEST_USERNAME);
        verify(userRepository).findByUsername(TEST_USERNAME);
    }
}
