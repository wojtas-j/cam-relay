package com.camrelay.repository;

import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the CRUD operations of {@link UserRepository} in the Cam Relay application.
 * @since 1.0
 */
@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTests {

    private static final String TEST_USER = "testuser";

    @Autowired
    private UserRepository repository;

    /**
     * Sets up the test environment by creating a sample UserEntity.
     * @since 1.0
     */
    @BeforeEach
    void setUp() {
        UserEntity testUser = UserEntity.builder()
                .username(TEST_USER)
                .password("password123P!")
                .roles(Set.of(Role.USER))
                .build();
        repository.save(testUser);
    }

    /**
     * Tests saving a user and finding it by username.
     * @since 1.0
     */
    @Test
    void shouldSaveAndFindByUsername() {
        // Act
        Optional<UserEntity> foundUser = repository.findByUsername(TEST_USER);

        // Assert
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo(TEST_USER);
    }

    /**
     * Tests finding a non-existent user by username.
     * @since 1.0
     */
    @Test
    void shouldReturnEmptyWhenUsernameNotFound() {
        // Act
        Optional<UserEntity> foundUser = repository.findByUsername("non-existent");

        // Assert
        assertThat(foundUser).isEmpty();
    }
}
