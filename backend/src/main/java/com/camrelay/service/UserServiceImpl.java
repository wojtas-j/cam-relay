package com.camrelay.service;

import com.camrelay.entity.UserEntity;
import com.camrelay.exception.UserNotFoundException;
import com.camrelay.repository.UserRepository;
import com.camrelay.service.interfaces.RefreshTokenService;
import com.camrelay.service.interfaces.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the {@link UserService} interface.
 * Provides business logic for user-related operations with security checks.
 * @since 1.0
 */
@Service
@AllArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    /**
     * Deletes a user's account and associated refresh tokens.
     * @param username the username of the user
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    public void deleteAccount(String username) {
        log.info("Deleting account for user: {}", username);
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        refreshTokenService.deleteByUser(user);
        userRepository.delete(user);
        log.info("Account deleted successfully for user: {}", username);
    }

    /**
     * Finds a user by username.
     * @param username the username to search for
     * @return the UserEntity
     * @throws UserNotFoundException if the user is not found
     * @since 1.0
     */
    @Override
    @Transactional(readOnly = true)
    public UserEntity findByUsername(String username) {
        log.info("Finding user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }
}
