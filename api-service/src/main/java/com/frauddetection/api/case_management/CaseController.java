package com.frauddetection.api.case_management;

import com.frauddetection.api.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final CaseWorkflowService workflowService;
    private final AuditService auditService;

    public CaseController(CaseWorkflowService workflowService, AuditService auditService) {
        this.workflowService = workflowService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Case> list() {
        return workflowService.list();
    }

    @GetMapping("/{id}")
    public Case get(@PathVariable UUID id) {
        return workflowService.get(id);
    }

    @PatchMapping("/{id}/status")
    public Case updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return workflowService.changeStatus(id, request.status(), request.resolution());
    }

    @PostMapping("/{id}/notes")
    public void addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        workflowService.addNote(id, request.note(), request.author());
    }

    @GetMapping("/{id}/timeline")
    public List<com.frauddetection.api.audit.CaseEvent> timeline(@PathVariable UUID id) {
        return workflowService.timeline(id);
    }

    @GetMapping("/{id}/verify")
    public AuditService.VerificationResult verify(@PathVariable UUID id) {
        workflowService.get(id);
        return auditService.verify(id);
    }

    public record StatusRequest(@NotBlank String status, String resolution) {
    }

    public record NoteRequest(@NotBlank String note, String author) {
    }
}
