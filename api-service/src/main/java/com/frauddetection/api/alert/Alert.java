package com.frauddetection.api.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "rule_score")
    private BigDecimal ruleScore;

    @Column(name = "ml_score")
    private BigDecimal mlScore;

    @Column(name = "graph_score")
    private BigDecimal graphScore;

    @Column(name = "combined_score")
    private BigDecimal combinedScore;

    private String category;
    private String severity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String status;

    protected Alert() {
    }

    public Alert(UUID transactionId, BigDecimal ruleScore, BigDecimal mlScore, BigDecimal graphScore,
                 BigDecimal combinedScore, String category, String severity) {
        this.alertId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.ruleScore = ruleScore;
        this.mlScore = mlScore;
        this.graphScore = graphScore;
        this.combinedScore = combinedScore;
        this.category = category;
        this.severity = severity;
        this.createdAt = Instant.now();
        this.status = "open";
    }

    public UUID getAlertId() { return alertId; }
    public UUID getTransactionId() { return transactionId; }
    public BigDecimal getRuleScore() { return ruleScore; }
    public BigDecimal getMlScore() { return mlScore; }
    public BigDecimal getGraphScore() { return graphScore; }
    public BigDecimal getCombinedScore() { return combinedScore; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
