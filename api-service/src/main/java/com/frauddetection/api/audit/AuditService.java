package com.frauddetection.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditService {
    public static final String GENESIS_HASH = "GENESIS";

    private final CaseEventRepository repository;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<UUID, Object> caseLocks = new ConcurrentHashMap<>();

    public AuditService(CaseEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public CaseEvent append(UUID caseId, String eventType, Object payload) {
        synchronized (caseLocks.computeIfAbsent(caseId, ignored -> new Object())) {
            try {
                JsonNode payloadNode = mapper.valueToTree(payload);
                String canonicalPayload = mapper.writeValueAsString(canonicalize(payloadNode));
                List<CaseEvent> events = repository.findByCaseIdOrderByCreatedAtAscEventIdAsc(caseId);
                String previousHash = events.isEmpty() ? GENESIS_HASH : events.get(events.size() - 1).getEventHash();
                // PostgreSQL TIMESTAMPTZ stores microseconds; hash the persisted precision.
                Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
                String eventHash = hash(previousHash, canonicalPayload, eventType, createdAt);
                return repository.save(new CaseEvent(caseId, eventType, payloadNode, previousHash, eventHash, createdAt));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Unable to canonicalize audit payload", error);
            }
        }
    }

    @Transactional(readOnly = true)
    public VerificationResult verify(UUID caseId) {
        List<CaseEvent> events = repository.findByCaseIdOrderByCreatedAtAscEventIdAsc(caseId);
        String previousHash = GENESIS_HASH;
        for (CaseEvent event : events) {
            try {
                String canonicalPayload = mapper.writeValueAsString(canonicalize(event.getPayload()));
                String expectedHash = hash(previousHash, canonicalPayload, event.getEventType(), event.getCreatedAt());
                if (!previousHash.equals(event.getPreviousHash()) || !expectedHash.equals(event.getEventHash())) {
                    return new VerificationResult(false, events.size(), event.getEventId(), "Hash or previous-hash mismatch");
                }
                previousHash = event.getEventHash();
            } catch (JsonProcessingException error) {
                return new VerificationResult(false, events.size(), event.getEventId(), "Payload cannot be canonicalized");
            }
        }
        return new VerificationResult(true, events.size(), null, "Chain is valid");
    }

    @Transactional(readOnly = true)
    public List<CaseEvent> events(UUID caseId) {
        return repository.findByCaseIdOrderByCreatedAtAscEventIdAsc(caseId);
    }

    private String hash(String previousHash, String payload, String eventType, Instant createdAt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (previousHash + payload + eventType + createdAt).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(child -> array.add(canonicalize(child)));
            return array;
        }
        ObjectNode object = mapper.createObjectNode();
        TreeSet<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        names.forEach(name -> object.set(name, canonicalize(node.get(name))));
        return object;
    }

    public record VerificationResult(boolean valid, int eventCount, UUID invalidEventId, String message) {
    }
}
