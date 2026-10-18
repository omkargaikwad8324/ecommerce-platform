package com.shop.auth.service;

import com.shop.auth.dto.*;
import com.shop.auth.entity.RefreshToken;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.User;
import com.shop.auth.exception.ApiException;
import com.shop.auth.repository.RefreshTokenRepository;
import com.shop.auth.repository.UserRepository;
import com.shop.auth.security.JwtService;
import com.shop.auth.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-token-days}")
    private long refreshDays;

    @Value("${app.security.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-minutes}")
    private long lockMinutes;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.LOCKED, "Account locked. Try again later.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
                attempts = 0;
            }
            user.setFailedAttempts(attempts);
            userRepository.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            // A revoked token is being reused: possible theft. Kill every session of this user.
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected. Please log in again.");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user);
        String raw = TokenHasher.generateRawToken();

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(TokenHasher.sha256(raw));
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(rt);

        return new AuthResponse(access, raw, "Bearer", jwtService.getAccessSeconds());
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name());
    }
}