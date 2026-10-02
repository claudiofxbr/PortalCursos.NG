package com.portalcursos.tokencontrol.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UsageEntryRequest(
        @NotBlank @Size(max = 128) String messageId,
        @NotNull Instant occurredAt,
        @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 120) String process,
        @NotBlank @Size(max = 80) String model,
        @Min(0) @Max(1_000_000_000L) long inputTokens,
        @Min(0) @Max(1_000_000_000L) long outputTokens,
        @Min(0) @Max(1_000_000_000L) long cacheCreationTokens,
        @Min(0) @Max(1_000_000_000L) long cacheReadTokens) {}
