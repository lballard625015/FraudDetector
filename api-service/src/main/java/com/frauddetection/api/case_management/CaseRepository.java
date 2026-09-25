package com.frauddetection.api.case_management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {
    Optional<Case> findByAlertId(UUID alertId);
    long countByStatusIn(List<String> statuses);
}
