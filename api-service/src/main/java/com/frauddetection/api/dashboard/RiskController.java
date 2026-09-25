package com.frauddetection.api.dashboard;

import com.frauddetection.api.account.Account;
import com.frauddetection.api.account.AccountRepository;
import com.frauddetection.api.alert.Alert;
import com.frauddetection.api.alert.AlertRepository;
import com.frauddetection.api.transaction.Transaction;
import com.frauddetection.api.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class RiskController {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;

    public RiskController(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          AlertRepository alertRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/{accountId}/risk")
    public AccountRisk risk(@PathVariable UUID accountId, @RequestParam(defaultValue = "6h") String range) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        Instant cutoff = Instant.now().minus(rangeDuration(range));
        List<Transaction> transactions = transactionRepository.findByFromAccountAccountIdOrToAccountAccountId(accountId, accountId);
        List<UUID> transactionIds = transactions.stream()
                .filter(transaction -> transaction.getOccurredAt() != null && transaction.getOccurredAt().isAfter(cutoff))
                .map(Transaction::getTransactionId)
                .toList();
        List<RiskPoint> points = alertRepository.findByTransactionIdInOrderByCreatedAtAsc(transactionIds).stream()
                .filter(alert -> alert.getCreatedAt().isAfter(cutoff) && alert.getCombinedScore() != null)
                .map(alert -> new RiskPoint(alert.getCreatedAt(), alert.getCombinedScore()))
                .toList();
        if (points.isEmpty()) {
            points = List.of(new RiskPoint(Instant.now(), account.getRiskScore() == null ? BigDecimal.ZERO : account.getRiskScore()));
        }
        return new AccountRisk(account.getAccountId(), account.getOwnerName(), account.getRiskScore(), points);
    }

    private Duration rangeDuration(String range) {
        return switch (range) {
            case "1d" -> Duration.ofDays(1);
            case "1w" -> Duration.ofDays(7);
            case "1m" -> Duration.ofDays(30);
            case "1y" -> Duration.ofDays(365);
            default -> Duration.ofHours(6);
        };
    }

    public record AccountRisk(UUID accountId, String ownerName, BigDecimal currentRisk, List<RiskPoint> points) {}
    public record RiskPoint(Instant timestamp, BigDecimal risk) {}
}
