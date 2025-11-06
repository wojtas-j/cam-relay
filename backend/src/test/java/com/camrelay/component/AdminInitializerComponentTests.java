package com.camrelay.component;

import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.entity.Role;
import com.camrelay.properties.AdminInitializerProperties;
import com.camrelay.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

/**
 * Tests the functionality of {@link AdminInitializerComponent} in the Cam Relay application.
 * Covers initialization behavior, validation handling, and correct admin creation logic.
 *
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class AdminInitializerComponentTests {

    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "Admin123!";
    private static final Set<Role> VALID_ROLES = Set.of(Role.ADMIN);
    private static final String INVALID_USERNAME = "ad";
    private static final String INVALID_PASSWORD = "weak";

    @Mock
    private AdminInitializerProperties properties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Validator validator;

    @InjectMocks
    private AdminInitializerComponent initializer;

    @BeforeEach
    void setUp() {
        reset(properties, userRepository, passwordEncoder, validator);
    }

    @Nested
    class SkipInitializationWhenUserExists {

        /**
         * Tests that admin initialization is skipped when at least one user already exists.
         *
         * @since 1.0
         */
        @Test
        void shouldSkipInitializationIfUserAlreadyExists() {
            // Arrange
            when(userRepository.count()).thenReturn(1L);

            // Act
            initializer.run(null);

            // Assert
            verify(userRepository, never()).save(any());
            verify(validator, never()).validate(any());
        }
    }

    @Nested
    class SkipInitializationWhenValidationFails {

        /**
         * Tests that admin initialization is skipped when configuration properties
         * fail validation using {@link CreateUserRequest} constraints.
         *
         * @since 1.0
         */
        @SuppressWarnings("unchecked")
        @Test
        void shouldSkipInitializationOnValidationErrors() {
            // Arrange
            when(userRepository.count()).thenReturn(0L);

            when(properties.getUsername()).thenReturn(INVALID_USERNAME);
            when(properties.getPassword()).thenReturn(INVALID_PASSWORD);

            ConstraintViolation<CreateUserRequest> violation = mock(ConstraintViolation.class);
            when(validator.validate(any(CreateUserRequest.class)))
                    .thenReturn(Set.of(violation));

            // Act
            initializer.run(null);

            // Assert
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class SuccessfulInitialization {

        /**
         * Tests successful creation of the admin user when all properties are valid
         * and no users exist yet.
         *
         * @since 1.0
         */
        @Test
        void shouldCreateAdminWhenPropertiesAreValid() {
            // Arrange
            when(userRepository.count()).thenReturn(0L);
            when(properties.getUsername()).thenReturn(VALID_USERNAME);
            when(properties.getPassword()).thenReturn(VALID_PASSWORD);
            when(properties.getRoles()).thenReturn(VALID_ROLES);

            when(validator.validate(any(CreateUserRequest.class)))
                    .thenReturn(Collections.emptySet());

            String encodedPassword = passwordEncoder.encode(VALID_PASSWORD);
            when(encodedPassword).thenReturn("hashed");

            // Act
            initializer.run(null);

            // Assert
            verify(userRepository).save(argThat(user ->
                    user.getUsername().equals(VALID_USERNAME)
                            && user.getPassword().equals("hashed")
                            && user.getRoles().contains(Role.ADMIN)
            ));
        }
    }
}
