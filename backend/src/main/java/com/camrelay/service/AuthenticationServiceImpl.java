package com.camrelay.service;

import com.camrelay.dto.user.UserResponse;
import com.camrelay.entity.UserEntity;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.repository.UserRepository;
import com.camrelay.service.interfaces.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Service implementation for handling user registration and user details loading in the Cam Relay application.
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService, UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Finds a user by their username.
     * @param username the username of the user to find
     * @return the {@link UserResponse} corresponding to the given username
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public UserResponse findByUsername(String username) {
        log.info("Finding user by username: {}", username);

        UserEntity entity = findEntityByUsername(username);

        log.info("User found: {}", username);
        return toResponse(entity);
    }

    /**
     * Loads user details by username for authentication.
     * @param username the username identifying the user
     * @return the {@link UserDetails} object for the user
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UserNotFoundException {
        log.info("Loading user details for username: {}", username);

        UserEntity entity = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found with username: {}", username);
                    return new UserNotFoundException("User not found with username: " + username);
                });

        log.info("User details loaded for: {}", username);
        return entity;
    }

    /**
     * Finds a user by their username.
     * @param username the username of the user to find
     * @return the {@link UserEntity} corresponding to the given username
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public UserEntity findEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new UserNotFoundException("User not found: " + username);
                });
    }

    /**
     * Maps a {@link UserEntity} to a {@link UserResponse} DTO.
     *
     * @param entity the user entity to map
     * @return mapped response DTO
     * @since 1.0
     */
    private UserResponse toResponse(UserEntity entity) {
        return new UserResponse(
                entity.getUsername(),
                entity.getRoles(),
                entity.getCreatedAt()
        );
    }
}
