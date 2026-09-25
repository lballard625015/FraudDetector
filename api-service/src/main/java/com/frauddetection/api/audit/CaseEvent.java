package com.frauddetection.api.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_events")
public class CaseEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "prev_hash", nullable = false)
    private String previousHash;

    @Column(name = "event_hash", nullable = false)
    private String eventHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CaseEvent() {
    }

    public CaseEvent(UUID caseId, String eventType, JsonNode payload, String previousHash, String eventHash, Instant createdAt) {
        this.eventId = UUID.randomUUID();
        this.caseId = caseId;
        this.eventType = eventType;
        this.payload = payload;
        this.previousHash = previousHash;
        this.eventHash = eventHash;
        this.createdAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public UUID getCaseId() { return caseId; }
    public String getEventType() { return eventType; }
    public JsonNode getPayload() { return payload; }
    public String getPreviousHash() { return previousHash; }
    public String getEventHash() { return eventHash; }
    public Instant getCreatedAt() { return createdAt; }
}
