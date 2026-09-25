package com.frauddetection.api.transaction;

import com.frauddetection.api.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account")
    private Account toAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "geo_lat")
    private BigDecimal geoLat;

    @Column(name = "geo_lon")
    private BigDecimal geoLon;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "is_synthetic_fraud", nullable = false)
    private boolean syntheticFraud;

    @Column(name = "fraud_pattern_type")
    private String fraudPatternType;

    protected Transaction() {
    }

    public Transaction(Account fromAccount, Account toAccount, BigDecimal amount, String currency, Instant occurredAt) {
        this.transactionId = UUID.randomUUID();
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public BigDecimal getGeoLat() {
        return geoLat;
    }

    public void setGeoLat(BigDecimal geoLat) {
        this.geoLat = geoLat;
    }

    public BigDecimal getGeoLon() {
        return geoLon;
    }

    public void setGeoLon(BigDecimal geoLon) {
        this.geoLon = geoLon;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public boolean isSyntheticFraud() {
        return syntheticFraud;
    }

    public String getFraudPatternType() {
        return fraudPatternType;
    }

    public void setFraudPatternType(String fraudPatternType) {
        this.fraudPatternType = fraudPatternType;
    }
}
