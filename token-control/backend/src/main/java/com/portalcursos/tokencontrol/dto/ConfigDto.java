package com.portalcursos.tokencontrol.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** resetDayOfWeek: 1=segunda … 7=domingo (ISO). resetTime: "HH:mm". */
public record ConfigDto(
        @NotBlank @Size(max = 40) String planName,
        @Min(1) @Max(7) int resetDayOfWeek,
        @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "use HH:mm") String resetTime,
        @NotBlank @Size(max = 60) String timezone,
        @Min(1) @Max(1_000_000_000_000L) long weeklyLimitTokens,
        /** Opcional: sem valor, o mês usa a referência estimada (semanal x dias do mês / 7). */
        @Min(1) @Max(1_000_000_000_000L) Long monthlyLimitTokens,
        boolean countCacheReads) {}
