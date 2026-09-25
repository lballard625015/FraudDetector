package com.frauddetection.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final Duration SESSION_DURATION = Duration.ofHours(8);

    private final AnalystRepository analystRepository;
    private final AnalystSessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AnalystRepository analystRepository, AnalystSessionRepository sessionRepository) {
        this.analystRepository = analystRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public AuthResponse register(String email, String displayName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (analystRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An analyst with that email already exists");
        }
        Analyst analyst = analystRepository.save(new Analyst(normalizedEmail, displayName.trim(), passwordEncoder.encode(password)));
        return createSession(analyst);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        Analyst analyst = analystRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(password, analyst.getPasswordHash())) {
            throw invalidCredentials();
        }
        return createSession(analyst);
    }

    @Transactional(readOnly = true)
    public Analyst authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        AnalystSession session = sessionRepository.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired or invalid"));
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired or invalid");
        }
        return analystRepository.findById(session.getAnalystId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Analyst account not found"));
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteById(token);
        }
    }

    private AuthResponse createSession(Analyst analyst) {
        String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(SESSION_DURATION);
        sessionRepository.save(new AnalystSession(token, analyst.getAnalystId(), expiresAt));
        return new AuthResponse(token, new AnalystProfile(analyst.getAnalystId(), analyst.getEmail(), analyst.getDisplayName(), analyst.getRole()), expiresAt);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    public record AnalystProfile(UUID analystId, String email, String displayName, String role) {}
    public record AuthResponse(String token, AnalystProfile analyst, Instant expiresAt) {}
}
