package com.frauddetection.api.case_management;

import com.frauddetection.api.audit.AuditService;
import com.frauddetection.api.alert.Alert;
import com.frauddetection.api.alert.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CaseWorkflowService {
    private static final List<String> VALID_STATUSES = List.of("investigating", "escalated", "dismissed", "resolved");

    private final CaseRepository caseRepository;
    private final AlertRepository alertRepository;
    private final AuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;

    public CaseWorkflowService(CaseRepository caseRepository, AlertRepository alertRepository, AuditService auditService,
                               SimpMessagingTemplate messagingTemplate) {
        this.caseRepository = caseRepository;
        this.alertRepository = alertRepository;
        this.auditService = auditService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<Case> list() {
        return caseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Case get(UUID caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> notFound(caseId));
    }

    @Transactional
    public Case changeStatus(UUID caseId, String status, String resolution) {
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported case status: " + status);
        }
        Case caseRecord = get(caseId);
        caseRecord.setStatus(status);
        caseRecord.setClosedAt(List.of("dismissed", "resolved").contains(status) ? Instant.now() : null);
        caseRecord.setResolution(resolution);
        if ("escalated".equals(status)) {
            caseRecord.setPriority("high");
            caseRecord.setEscalationReason("Analyst escalation");
            caseRecord.setEscalatedAt(caseRecord.getEscalatedAt() == null ? Instant.now() : caseRecord.getEscalatedAt());
        }
        Case saved = caseRepository.save(caseRecord);
        alertRepository.findById(caseRecord.getAlertId()).ifPresent(alert -> {
            alert.setStatus(status);
            alertRepository.save(alert);
        });
        auditService.append(caseId, "status_change", Map.of("status", status, "resolution", resolution == null ? "" : resolution));
        messagingTemplate.convertAndSend("/topic/cases", saved);
        return saved;
    }

    @Transactional
    public void addNote(UUID caseId, String note, String author) {
        get(caseId);
        auditService.append(caseId, "analyst_note", Map.of("note", note, "author", author == null ? "unknown" : author));
        messagingTemplate.convertAndSend("/topic/cases", Map.of("case_id", caseId, "event_type", "analyst_note"));
    }

    @Transactional(readOnly = true)
    public List<com.frauddetection.api.audit.CaseEvent> timeline(UUID caseId) {
        get(caseId);
        return auditService.events(caseId);
    }

    private ResponseStatusException notFound(UUID caseId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId);
    }
}
