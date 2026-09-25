package com.frauddetection.api.signal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signals")
public class Signal {
    @Id
    @Column(name = "signal_id")
    private UUID signalId;
    @Column(name = "transaction_id") private UUID transactionId;
    @Column(name = "rule_score") private BigDecimal ruleScore;
    @Column(name = "ml_score") private BigDecimal mlScore;
    @Column(name = "graph_score") private BigDecimal graphScore;
    @Column(name = "combined_score") private BigDecimal combinedScore;
    private String category;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Signal() {}

    public Signal(UUID transactionId, BigDecimal ruleScore, BigDecimal mlScore, BigDecimal graphScore,
                  BigDecimal combinedScore, String category) {
        this.signalId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.ruleScore = ruleScore;
        this.mlScore = mlScore;
        this.graphScore = graphScore;
        this.combinedScore = combinedScore;
        this.category = category;
        this.createdAt = Instant.now();
    }

    public UUID getSignalId() { return signalId; }
    public UUID getTransactionId() { return transactionId; }
    public BigDecimal getRuleScore() { return ruleScore; }
    public BigDecimal getMlScore() { return mlScore; }
    public BigDecimal getGraphScore() { return graphScore; }
    public BigDecimal getCombinedScore() { return combinedScore; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}