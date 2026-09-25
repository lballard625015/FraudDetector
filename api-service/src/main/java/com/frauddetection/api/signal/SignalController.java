package com.frauddetection.api.signal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/signals")
public class SignalController {
    private final SignalRepository repository;

    public SignalController(SignalRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Signal> list() { return repository.findAll(); }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public Signal ingest(@RequestBody SignalRequest request) {
        return repository.save(new Signal(request.transactionId(), request.ruleScore(), request.mlScore(),
                request.graphScore(), request.combinedScore(), request.category()));
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() { repository.deleteAllInBatch(); }

    public record SignalRequest(UUID transactionId, BigDecimal ruleScore, BigDecimal mlScore,
                                BigDecimal graphScore, BigDecimal combinedScore, String category) {}
}