package com.frauddetection.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AnalystSessionRepository extends JpaRepository<AnalystSession, String> {
    void deleteByExpiresAtBefore(Instant time);
    long deleteByAnalystId(UUID analystId);
}
