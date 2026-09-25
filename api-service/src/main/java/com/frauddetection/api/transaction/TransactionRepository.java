package com.frauddetection.api.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
	long countByOccurredAtIsNotNull();
	List<Transaction> findByFromAccountAccountIdOrToAccountAccountId(UUID fromAccountId, UUID toAccountId);
}
