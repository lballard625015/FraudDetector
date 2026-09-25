package com.frauddetection.api.alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertRepository repository;
    private final AlertIngestionService ingestionService;

    public AlertController(AlertRepository repository, AlertIngestionService ingestionService) {
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    @GetMapping
    public List<Alert> list(@RequestParam(required = false) String status) {
        return status == null ? repository.findAll() : repository.findByStatusOrderByCreatedAtDesc(status);
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public Alert ingest(@Valid @RequestBody AlertIngestionService.AlertRequest request) {
        return ingestionService.ingest(request);
    }
}
