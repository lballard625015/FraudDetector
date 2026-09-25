package com.frauddetection.api.signal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SignalRepository extends JpaRepository<Signal, UUID> {
}