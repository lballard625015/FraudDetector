package com.frauddetection.api.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "risk_score", nullable = false)
    private BigDecimal riskScore;

    @Column(nullable = false)
    private String status;

    protected Account() {
    }

    public Account(String ownerName) {
        this.accountId = UUID.randomUUID();
        this.ownerName = ownerName;
        this.createdAt = Instant.now();
        this.riskScore = BigDecimal.ZERO;
        this.status = "active";
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
