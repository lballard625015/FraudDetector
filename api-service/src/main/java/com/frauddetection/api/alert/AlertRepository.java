package com.frauddetection.api.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    Optional<Alert> findFirstByTransactionIdAndCategoryAndStatus(UUID transactionId, String category, String status);
    List<Alert> findByTransactionIdInOrderByCreatedAtAsc(List<UUID> transactionIds);
}
