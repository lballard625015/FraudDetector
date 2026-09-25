package com.frauddetection.api.alert;

import com.frauddetection.api.case_management.Case;
import com.frauddetection.api.case_management.CaseRepository;
import com.frauddetection.api.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertIngestionService {
    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BigDecimal caseThreshold;

    public AlertIngestionService(AlertRepository alertRepository, CaseRepository caseRepository,
                                 AuditService auditService, SimpMessagingTemplate messagingTemplate,
                                 @Value("${fraud.case.auto-create-threshold:0.7}") BigDecimal caseThreshold) {
        this.alertRepository = alertRepository;
        this.caseRepository = caseRepository;
        this.auditService = auditService;
        this.messagingTemplate = messagingTemplate;
        this.caseThreshold = caseThreshold;
    }

    @Transactional
    public Alert ingest(AlertRequest request) {
        if (request.transactionId() != null) {
            Alert existing = alertRepository.findFirstByTransactionIdAndCategoryAndStatus(
                    request.transactionId(), request.category(), "open").orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Alert alert = alertRepository.save(new Alert(
                request.transactionId(), request.ruleScore(), request.mlScore(), request.graphScore(),
                request.combinedScore(), request.category(), request.severity()));
        messagingTemplate.convertAndSend("/topic/alerts", alert);
        if (request.combinedScore().compareTo(caseThreshold) >= 0) {
            Case createdCase = caseRepository.save(new Case(alert.getAlertId()));
            auditService.append(createdCase.getCaseId(), "alert_created", Map.of(
                    "alert_id", alert.getAlertId().toString(),
                    "combined_score", request.combinedScore(),
                    "category", request.category()));
            messagingTemplate.convertAndSend("/topic/cases", createdCase);
        }
        return alert;
    }

    public record AlertRequest(
            UUID transactionId,
            BigDecimal ruleScore,
            BigDecimal mlScore,
            BigDecimal graphScore,
            BigDecimal combinedScore,
            String category,
            String severity
    ) {
    }
}
