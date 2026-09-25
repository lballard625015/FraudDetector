package com.frauddetection.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "analyst_sessions")
public class AnalystSession {
    @Id
    @Column(nullable = false, length = 128)
    private String token;

    @Column(name = "analyst_id", nullable = false)
    private java.util.UUID analystId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AnalystSession() {
    }

    public AnalystSession(String token, java.util.UUID analystId, Instant expiresAt) {
        this.token = token;
        this.analystId = analystId;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public java.util.UUID getAnalystId() { return analystId; }
    public Instant getExpiresAt() { return expiresAt; }
}
