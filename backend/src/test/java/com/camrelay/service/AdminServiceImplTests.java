package com.camrelay.service;

import com.camrelay.dto.user.AdminGetUsersResponse;
import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.repository.UserRepository;
import com.camrelay.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the functionality of {@link AdminServiceImpl} in the Cam Relay application.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AdminServiceImplTests {

    private static final Long TEST_USER_ID = 2L;
    private static final Long INVALID_USER_ID = Long.MAX_VALUE;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encodedPassword123";
    private static final LocalDateTime TEST_CREATED_AT = LocalDateTime.now();
    private static final Set<Role> TEST_ROLES = Set.of(Role.USER);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminServiceImpl adminService;
    
    private CreateUserRequest createUserRequest;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        createUserRequest = new CreateUserRequest(TEST_USERNAME, TEST_PASSWORD, Set.of(Role.USER, Role.ADMIN));
        userEntity = UserEntity.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .password("Password123!")
                .roles(TEST_ROLES)
                .createdAt(TEST_CREATED_AT)
                .build();

        reset(userRepository, refreshTokenService);
    }

    @Nested
    class CreateUser {
        /**
         * Tests successful user creation.
         * @since 1.0
         */
        @SuppressWarnings({"ConstantConditions", "DataFlowIssue"})
        @Test
        void shouldRegisterUserSuccessfully() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

            // Act
            UserResponse result = adminService.createUser(createUserRequest);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.username()).isEqualTo(TEST_USERNAME);
            assertThat(result.roles()).hasSize(1).contains(Role.USER);
            verify(userRepository).findByUsername(TEST_USERNAME);
            verify(passwordEncoder).encode(TEST_PASSWORD);
            verify(userRepository).save(any(UserEntity.class));
        }

        /**
         * Tests throwing AuthenticationException when username is already taken during user creation.
         * @since 1.0
         */
        @Test
        void shouldThrowAuthenticationExceptionForTakenUsername() {
            // Arrange
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(userEntity));

            // Act & Assert
            AuthenticationException exception = assertThrows(AuthenticationException.class,
                    () -> adminService.createUser(createUserRequest));
            assertThat(exception.getMessage()).isEqualTo("Username already taken");
            verify(userRepository).findByUsername(TEST_USERNAME);
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any(UserEntity.class));
        }
    }

    @Nested
    class GetAllUsersTests {
        /**
         * Tests retrieving a paginated list of users successfully.
         * @since 1.0
         */
        @Test
        void shouldGetAllUsersSuccessfully() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
            Page<UserEntity> usersPage = new PageImpl<>(List.of(userEntity), pageable, 1);
            when(userRepository.findAll(any(Pageable.class))).thenReturn(usersPage);

            // Act
            Page<AdminGetUsersResponse> result = adminService.getAllUsers(pageable);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            AdminGetUsersResponse userResponse = result.getContent().getFirst();
            assertEquals(TEST_USER_ID, userResponse.id());
            assertEquals(TEST_USERNAME, userResponse.username());
            assertEquals(TEST_CREATED_AT, userResponse.createdAt());
            assertEquals(TEST_ROLES, userResponse.roles());
            verify(userRepository).findAll(any(Pageable.class));
        }

        /**
         * Tests retrieving an empty paginated list when no users exist.
         * @since 1.0
         */
        @Test
        void shouldReturnEmptyPageWhenNoUsersExist() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
            Page<UserEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(userRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

            // Act
            Page<AdminGetUsersResponse> result = adminService.getAllUsers(pageable);

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
            verify(userRepository).findAll(any(Pageable.class));
        }

        /**
         * Tests retrieving users with different page size and ascending sort.
         * @since 1.0
         */
        @Test
        void shouldGetAllUsersWithDifferentPageSizeAndSort() {
            // Arrange
            Pageable inputPageable = PageRequest.of(1, 5, Sort.by("createdAt").ascending());
            Pageable servicePageable = PageRequest.of(inputPageable.getPageNumber(), inputPageable.getPageSize(), Sort.by("createdAt").descending());
            Page<UserEntity> usersPage = new PageImpl<>(List.of(userEntity), servicePageable, 1L);
            when(userRepository.findAll(servicePageable)).thenReturn(usersPage);

            // Act
            Page<AdminGetUsersResponse> result = adminService.getAllUsers(inputPageable);

            // Assert
            assertNotNull(result);
            assertEquals(6, result.getTotalElements());
            assertEquals(5, result.getPageable().getPageSize());
            assertEquals(Sort.by("createdAt").descending(), result.getPageable().getSort());
            AdminGetUsersResponse userResponse = result.getContent().getFirst();
            assertEquals(TEST_USER_ID, userResponse.id());
            verify(userRepository).findAll(servicePageable);
        }
    }

    @Nested
    class DeleteUserTests {
        /**
         * Tests deleting a user successfully.
         * @since 1.0
         */
        @Test
        void shouldDeleteUserSuccessfully() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(userEntity));
            doNothing().when(refreshTokenService).deleteByUser(userEntity);
            doNothing().when(userRepository).delete(userEntity);

            // Act
            adminService.deleteUser(TEST_USER_ID);

            // Assert
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService).deleteByUser(userEntity);
            verify(userRepository).delete(userEntity);
        }

        /**
         * Tests throwing UserNotFoundException when user is not found.
         * @since 1.0
         */
        @Test
        void shouldThrowUserNotFoundExceptionWhenUserNotFound() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> adminService.deleteUser(TEST_USER_ID));
            assertEquals("User not found with ID: " + TEST_USER_ID, exception.getMessage());
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService, never()).deleteByUser(any());
            verify(userRepository, never()).delete(any());
        }

        /**
         * Tests throwing UserNotFoundException for extremely large user ID.
         * @since 1.0
         */
        @Test
        void shouldThrowUserNotFoundExceptionForLargeUserId() {
            // Arrange
            when(userRepository.findById(INVALID_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> adminService.deleteUser(INVALID_USER_ID));
            assertEquals("User not found with ID: " + INVALID_USER_ID, exception.getMessage());
            verify(userRepository).findById(INVALID_USER_ID);
            verify(refreshTokenService, never()).deleteByUser(any());
            verify(userRepository, never()).delete(any());
        }

        /**
         * Tests handling exception from refreshTokenService.deleteByUser.
         * @since 1.0
         */
        @Test
        void shouldPropagateExceptionFromRefreshTokenService() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(userEntity));
            doThrow(new RuntimeException("Database error")).when(refreshTokenService).deleteByUser(userEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> adminService.deleteUser(TEST_USER_ID));
            assertEquals("Database error", exception.getMessage());
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService).deleteByUser(userEntity);
            verify(userRepository, never()).delete(any());
        }
    }

    @Nested
    class DeleteRefreshTokensTests {
        /**
         * Tests deleting refresh tokens successfully.
         * @since 1.0
         */
        @Test
        void shouldDeleteRefreshTokensSuccessfully() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(userEntity));
            doNothing().when(refreshTokenService).deleteByUser(userEntity);

            // Act
            adminService.deleteRefreshTokens(TEST_USER_ID);

            // Assert
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService).deleteByUser(userEntity);
        }

        /**
         * Tests throwing UserNotFoundException when user is not found.
         * @since 1.0
         */
        @Test
        void shouldThrowUserNotFoundExceptionWhenUserNotFound() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> adminService.deleteRefreshTokens(TEST_USER_ID));
            assertEquals("User not found with ID: " + TEST_USER_ID, exception.getMessage());
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService, never()).deleteByUser(any());
        }

        /**
         * Tests throwing UserNotFoundException for extremely large user ID.
         * @since 1.0
         */
        @Test
        void shouldThrowUserNotFoundExceptionForLargeUserId() {
            // Arrange
            when(userRepository.findById(INVALID_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> adminService.deleteRefreshTokens(INVALID_USER_ID));
            assertEquals("User not found with ID: " + INVALID_USER_ID, exception.getMessage());
            verify(userRepository).findById(INVALID_USER_ID);
            verify(refreshTokenService, never()).deleteByUser(any());
        }

        /**
         * Tests handling exception from refreshTokenService.deleteByUser.
         * @since 1.0
         */
        @Test
        void shouldPropagateExceptionFromRefreshTokenService() {
            // Arrange
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(userEntity));
            doThrow(new RuntimeException("Database error")).when(refreshTokenService).deleteByUser(userEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> adminService.deleteRefreshTokens(TEST_USER_ID));
            assertEquals("Database error", exception.getMessage());
            verify(userRepository).findById(TEST_USER_ID);
            verify(refreshTokenService).deleteByUser(userEntity);
        }
    }
}
