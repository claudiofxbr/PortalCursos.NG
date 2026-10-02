package com.portalcursos.tokencontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "token_usage_entries")
public class UsageEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, updatable = false, length = 128)
    private String messageId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "hour_bucket", nullable = false, updatable = false)
    private Instant hourBucket;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(nullable = false, length = 120)
    private String process;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_creation_tokens", nullable = false)
    private long cacheCreationTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UsageEntry() {}

    public UsageEntry(String messageId, Instant occurredAt, Instant hourBucket, String sessionId, String process,
            String model, long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens,
            Instant createdAt) {
        this.messageId = messageId;
        this.occurredAt = occurredAt;
        this.hourBucket = hourBucket;
        this.sessionId = sessionId;
        this.process = process;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheCreationTokens = cacheCreationTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getHourBucket() { return hourBucket; }
    public String getSessionId() { return sessionId; }
    public String getProcess() { return process; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getCacheCreationTokens() { return cacheCreationTokens; }
    public long getCacheReadTokens() { return cacheReadTokens; }
}
