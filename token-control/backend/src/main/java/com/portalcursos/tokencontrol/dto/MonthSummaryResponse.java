package com.portalcursos.tokencontrol.dto;

import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Projection;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Slice;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Totals;
import java.time.Instant;
import java.util.List;

/** Visão "Mês atual": do dia 1 até hoje, com o que falta até o fim do mês. */
public record MonthSummaryResponse(
        ConfigDto config,
        Period period,
        long limit,
        /** true quando não há orçamento mensal configurado e o limite é derivado do semanal. */
        boolean limitEstimated,
        long used,
        long remaining,
        double usedPct,
        Totals totals,
        Projection projection,
        List<Day> daily,
        List<Slice> byProcess,
        List<Slice> byModel) {

    public record Period(Instant start, Instant end, int daysInMonth, int dayOfMonth, long secondsRemaining,
            double elapsedPct) {}
}
