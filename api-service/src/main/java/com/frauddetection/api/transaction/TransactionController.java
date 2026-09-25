package com.frauddetection.api.transaction;

import com.frauddetection.api.account.Account;
import com.frauddetection.api.account.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionController(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    // Resolve account references before persistence so invalid transfers fail clearly.
    @GetMapping
    public List<Transaction> list() {
        return transactionRepository.findAll();
    }

    @GetMapping("/{id}")
    public Transaction get(@PathVariable UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@Valid @RequestBody TransactionRequest request) {
        Account fromAccount = findAccount(request.fromAccountId());
        Account toAccount = findAccount(request.toAccountId());
        Transaction transaction = new Transaction(fromAccount, toAccount, request.amount(), request.currency(), request.occurredAt());
        transaction.setDeviceId(request.deviceId());
        transaction.setGeoLat(request.geoLat());
        transaction.setGeoLon(request.geoLon());
        transaction.setFraudPatternType(request.fraudPatternType());
        return transactionRepository.save(transaction);
    }

    @PutMapping("/{id}")
    public Transaction update(@PathVariable UUID id, @Valid @RequestBody TransactionUpdateRequest request) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setDeviceId(request.deviceId());
        transaction.setGeoLat(request.geoLat());
        transaction.setGeoLon(request.geoLon());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setFraudPatternType(request.fraudPatternType());
        return transactionRepository.save(transaction);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id);
        }
        transactionRepository.deleteById(id);
    }

    private Account findAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found: " + id));
    }

    public record TransactionRequest(
            UUID fromAccountId,
            UUID toAccountId,
            @NotNull @PositiveOrZero BigDecimal amount,
            @NotNull String currency,
            UUID deviceId,
            BigDecimal geoLat,
            BigDecimal geoLon,
            @NotNull Instant occurredAt,
            String fraudPatternType
    ) {
    }

    public record TransactionUpdateRequest(
            @NotNull @PositiveOrZero BigDecimal amount,
            @NotNull String currency,
            UUID deviceId,
            BigDecimal geoLat,
            BigDecimal geoLon,
            @NotNull Instant occurredAt,
            String fraudPatternType
    ) {
    }
}
