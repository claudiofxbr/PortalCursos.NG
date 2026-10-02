package com.portalcursos.tokencontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.service.CycleCalculator;
import com.portalcursos.tokencontrol.service.CycleWindow;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CycleCalculatorTest {

    // reset segunda 09:00 em São Paulo (UTC-3, sem DST desde 2019)
    private PlanConfig sp() {
        return new PlanConfig("Pro", 1, LocalTime.of(9, 0), "America/Sao_Paulo", 1000, false, Instant.EPOCH);
    }

    @Test
    void meioDaSemanaPegaUltimoResetEProximo() {
        // quinta 2026-10-01 12:00Z -> último reset seg 2026-09-28 09:00 -03 = 12:00Z
        CycleWindow w = CycleCalculator.cycleAt(sp(), Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-09-28T12:00:00Z"));
        assertThat(w.end()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
    }

    @Test
    void instanteExatoDoResetPertenceAoNovoCiclo() {
        CycleWindow w = CycleCalculator.cycleAt(sp(), Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
        CycleWindow before = CycleCalculator.cycleAt(sp(), Instant.parse("2026-10-05T11:59:59Z"));
        assertThat(before.end()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
    }

    @Test
    void diaDoResetAntesDoHorarioUsaCicloAnterior() {
        // segunda 2026-10-05 08:00 -03 (11:00Z) ainda é o ciclo que começou 09-28
        CycleWindow w = CycleCalculator.cycleAt(sp(), Instant.parse("2026-10-05T11:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-09-28T12:00:00Z"));
    }

    @Test
    void cicloTemSempreSeteDiasLocaisMesmoComDst() {
        // Nova York: DST termina 2026-11-01. Reset domingo 00:00 local.
        PlanConfig ny = new PlanConfig("Pro", 7, LocalTime.MIDNIGHT, "America/New_York", 1000, false, Instant.EPOCH);
        CycleWindow w = CycleCalculator.cycleAt(ny, Instant.parse("2026-10-28T12:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-10-25T04:00:00Z")); // 00:00 EDT
        assertThat(w.end()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));   // 00:00 EDT (a troca é 02:00)
        CycleWindow next = CycleCalculator.cycleAt(ny, w.end());
        assertThat(next.end()).isEqualTo(Instant.parse("2026-11-08T05:00:00Z")); // 00:00 EST: semana de 168h+1h
    }

    @Test
    void previousRetornaCicloAdjacente() {
        CycleWindow w = CycleCalculator.cycleAt(sp(), Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(w.previous().end()).isEqualTo(w.start());
    }
}
