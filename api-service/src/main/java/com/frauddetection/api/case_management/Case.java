package com.frauddetection.api.case_management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class Case {
    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String priority;

    @Column(name = "escalation_reason")
    private String escalationReason;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    private String resolution;

    protected Case() {
    }

    public Case(UUID alertId) {
        this.caseId = UUID.randomUUID();
        this.alertId = alertId;
        this.openedAt = Instant.now();
        this.status = "open";
        this.priority = "normal";
    }

    public UUID getCaseId() { return caseId; }
    public UUID getAlertId() { return alertId; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
