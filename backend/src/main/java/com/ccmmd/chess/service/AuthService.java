package com.ccmmd.chess.service;

import com.ccmmd.chess.domain.User;
import com.ccmmd.chess.dto.RegisterRequest;
import com.ccmmd.chess.dto.UserDto;
import com.ccmmd.chess.exception.EmailAlreadyUsedException;
import com.ccmmd.chess.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("An account already exists for " + email);
        }
        User user = new User(
                UUID.randomUUID().toString(),
                email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()));
        return toDto(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public Optional<String> findIdByEmail(String email) {
        return users.findByEmailIgnoreCase(email).map(User::getId);
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email).map(AuthService::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(String id) {
        return users.findById(id).map(AuthService::toDto);
    }

    private static UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
