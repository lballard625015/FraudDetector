package com.frauddetection.api.dashboard;

import com.frauddetection.api.alert.AlertRepository;
import com.frauddetection.api.case_management.CaseRepository;
import com.frauddetection.api.transaction.TransactionRepository;
import com.frauddetection.api.signal.SignalRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final SignalRepository signalRepository;

    public DashboardController(TransactionRepository transactionRepository, AlertRepository alertRepository,
                               CaseRepository caseRepository, SignalRepository signalRepository) {
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.caseRepository = caseRepository;
        this.signalRepository = signalRepository;
    }

    @GetMapping("/summary")
    public Summary summary() {
        return new Summary(
                signalRepository.count(),
                alertRepository.countByStatus("open"),
                caseRepository.countByStatusIn(List.of("open", "investigating", "escalated")));
    }

    public record Summary(long signalsFlagged, long alertsOpened, long casesActive) {
    }
}