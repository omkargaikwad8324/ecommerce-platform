package com.shop.auth.config;

import com.shop.auth.entity.Role;
import com.shop.auth.entity.User;
import com.shop.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@shop.com")) {
            User admin = new User();
            admin.setFullName("Admin");
            admin.setEmail("admin@shop.com");
            admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
    }
}