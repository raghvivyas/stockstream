package com.stockstream.scheduler;

import com.stockstream.entity.UserEntity;
import com.stockstream.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds default users on first startup.
 * Credentials: admin/password123 and demo/password123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(UserEntity.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("password123"))
                    .role("ADMIN")
                    .build());

            userRepository.save(UserEntity.builder()
                    .username("demo")
                    .password(passwordEncoder.encode("password123"))
                    .role("USER")
                    .build());

            log.info("=================================================");
            log.info("Default users created:");
            log.info("  admin / password123  (ADMIN)");
            log.info("  demo  / password123  (USER)");
            log.info("=================================================");
        }
    }
}
