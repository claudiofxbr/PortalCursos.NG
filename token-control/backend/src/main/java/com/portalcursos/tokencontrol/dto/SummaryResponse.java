package com.portalcursos.tokencontrol.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SummaryResponse(
        ConfigDto config,
        Cycle cycle,
        long limit,
        long used,
        long remaining,
        double usedPct,
        Totals totals,
        Projection projection,
        long last5hTokens,
        List<Day> daily,
        List<Slice> byProcess,
        List<Slice> byModel) {

    public record Cycle(Instant start, Instant end, long secondsRemaining, double elapsedPct) {}

    public record Totals(long input, long output, long cacheCreation, long cacheRead, long messages) {}

    /** Null-safe: projection só existe após 1h de ciclo e algum consumo. */
    public record Projection(long projectedTotal, double projectedPct, long dailyAverage, boolean willExceed,
            Instant exhaustionAt) {}

    public record Day(int index, LocalDate date, long tokens, long cumulative) {}

    public record Slice(String name, long tokens, long messages, double pct) {}
}
