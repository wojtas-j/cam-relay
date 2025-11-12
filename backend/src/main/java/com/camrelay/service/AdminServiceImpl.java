package com.camrelay.service;

import com.camrelay.dto.user.AdminGetUsersResponse;
import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.dto.user.UserResponse;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.AuthenticationException;
import com.camrelay.exception.UserAlreadyExistsException;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.repository.UserRepository;
import com.camrelay.service.interfaces.AdminService;
import com.camrelay.service.interfaces.RefreshTokenService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementation of the {@link AdminService} interface.
 * Provides business logic for admin-related operations with security checks.
 * @since 1.0
 */
@Service
@AllArgsConstructor
@Slf4j
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Retrieves a paginated list of all users, excluding sensitive fields like password, API key, and ID.
     * @param pageable pagination information (page number, size)
     * @return a {@link Page} of {@link AdminGetUsersResponse} objects
     * @since 1.0
     */
    @Override
    public Page<AdminGetUsersResponse> getAllUsers(Pageable pageable) {
        log.info("Fetching all users for admin request");
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").descending());
        Page<UserEntity> users = userRepository.findAll(sortedPageable);
        Page<AdminGetUsersResponse> userDTOs = users.map(user -> new AdminGetUsersResponse(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt(),
                user.getRoles()
        ));
        log.info("Retrieved {} total users", userDTOs.getTotalElements());
        return userDTOs;
    }

    /**
     * Registers a new user with the provided details.
     * @param request the create user request containing username and password.
     * @return the created UserResponse
     * @throws UserAlreadyExistsException if the username is already taken.
     * @since 1.0
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Registering new user: {}", request.username());
        if (userRepository.findByUsername(request.username()).isPresent()) {
            log.error("Username already taken: {}", request.username());
            throw new UserAlreadyExistsException("Username already taken");
        }

        UserEntity user = UserEntity.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .roles(request.roles())
                .build();
        UserEntity savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());
        return new UserResponse(savedUser.getUsername(), savedUser.getRoles(), savedUser.getCreatedAt());
    }

    /**
     * Deletes a user and their associated refresh tokens.
     * @param id the ID of the user to delete
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        refreshTokenService.deleteByUser(user);
        userRepository.delete(user);
        log.info("User deleted successfully with ID: {}", id);
    }

    /**
     * Deletes all refresh tokens for a specific user.
     * @param id the ID of the user whose tokens are to be deleted
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public void deleteRefreshTokens(Long id) {
        log.info("Deleting refresh tokens for user with ID: {}", id);
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        refreshTokenService.deleteByUser(user);
        log.info("Refresh tokens deleted successfully for user with ID: {}", id);
    }
}
