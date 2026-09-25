package com.frauddetection.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnalystRepository extends JpaRepository<Analyst, UUID> {
    Optional<Analyst> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
