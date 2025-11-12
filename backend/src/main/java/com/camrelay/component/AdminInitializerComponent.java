package com.camrelay.component;

import com.camrelay.dto.user.CreateUserRequest;
import com.camrelay.entity.Role;
import com.camrelay.entity.UserEntity;
import com.camrelay.properties.AdminInitializerProperties;
import com.camrelay.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Component responsible for creating the initial admin user on the first
 * application startup. The admin is created only if the user database
 * is empty (i.e., no users exist).
 *
 * <p>The admin credentials and roles are loaded from
 * {@link com.camrelay.properties.AdminInitializerProperties}.
 * The password is securely hashed using {@link org.springframework.security.crypto.password.PasswordEncoder}.</p>
 *
 * <p>This ensures safe, environment-driven bootstrap of the first user.</p>
 *
 * @since 1.0
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializerComponent implements ApplicationRunner {
    private final AdminInitializerProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args){
      if (userRepository.count() != 0){
          log.info("Admin already exists - skipping");
          return;
      }

      CreateUserRequest request = new CreateUserRequest(properties.getUsername(), properties.getPassword(), Set.of(Role.ADMIN, Role.RECEIVER));
      var violations = validator.validate(request);
      if (!violations.isEmpty()) {

          log.error("Admin initializer validation failed – admin user WILL NOT be created.");

          violations.forEach(v ->
                  log.error("Validation error on {}: {}", v.getPropertyPath(), v.getMessage())
          );

          log.warn("Skipping admin initialization due to invalid initializer.* properties.");
          return;
        }

      UserEntity admin = UserEntity.builder()
              .username(request.username())
              .password(passwordEncoder.encode(request.password()))
              .roles(properties.getRoles())
              .build();

      userRepository.save(admin);
      log.info("Initialized admin user for database");
    }
}
