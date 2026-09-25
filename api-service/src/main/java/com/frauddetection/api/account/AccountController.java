package com.frauddetection.api.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountRepository repository;

    public AccountController(AccountRepository repository) {
        this.repository = repository;
    }

    // Phase 0 keeps CRUD direct and leaves workflow rules to later services.
    @GetMapping
    public List<Account> list() {
        return repository.findAll();
    }

    @GetMapping("/search")
    public List<Account> search(@RequestParam(defaultValue = "") String q) {
        return repository.search(q.trim(), PageRequest.of(0, 20));
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@Valid @RequestBody AccountRequest request) {
        return repository.save(new Account(request.ownerName()));
    }

    @PutMapping("/{id}")
    public Account update(@PathVariable UUID id, @Valid @RequestBody AccountUpdateRequest request) {
        Account account = repository.findById(id).orElseThrow(() -> notFound(id));
        account.setOwnerName(request.ownerName());
        account.setRiskScore(request.riskScore() == null ? account.getRiskScore() : request.riskScore());
        account.setStatus(request.status());
        return repository.save(account);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id);
    }

    public record AccountRequest(@NotBlank String ownerName) {
    }

    public record AccountUpdateRequest(
            @NotBlank String ownerName,
            BigDecimal riskScore,
            @NotBlank String status
    ) {
    }
}
