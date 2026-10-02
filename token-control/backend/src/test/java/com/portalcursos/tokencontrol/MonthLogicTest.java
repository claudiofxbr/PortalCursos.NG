package com.portalcursos.tokencontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.repository.HourUsage;
import com.portalcursos.tokencontrol.service.CycleWindow;
import com.portalcursos.tokencontrol.service.MonthCalculator;
import com.portalcursos.tokencontrol.service.SummaryService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthLogicTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void mesVaiDoDia1AoDia1SeguinteNoFusoConfigurado() {
        CycleWindow w = MonthCalculator.windowAt(SP, Instant.parse("2026-10-15T12:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z")); // 00:00 -03
        assertThat(w.end()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void viradaDeMesUsaDataLocalNaoUtc() {
        // 2026-11-01T01:00Z ainda é 31/10 22:00 em São Paulo → continua em outubro
        CycleWindow w = MonthCalculator.windowAt(SP, Instant.parse("2026-11-01T01:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        // e 03:00Z em ponto já é novembro (instante exato do início pertence ao novo mês)
        assertThat(MonthCalculator.windowAt(SP, Instant.parse("2026-11-01T03:00:00Z")).start())
                .isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void mesComMudancaDeHorarioDeVeraoTem721HorasMasMesmoNumeroDeDias() {
        ZoneId ny = ZoneId.of("America/New_York"); // DST termina 2026-11-01
        CycleWindow w = MonthCalculator.windowAt(ny, Instant.parse("2026-11-15T12:00:00Z"));
        assertThat(w.start()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z")); // 00:00 EDT
        assertThat(w.end()).isEqualTo(Instant.parse("2026-12-01T05:00:00Z"));   // 00:00 EST
    }

    @Test
    @SuppressWarnings("unchecked")
    void diarioDoMesTemUmItemPorDiaEAcumula() throws Exception {
        Method m = SummaryService.class.getDeclaredMethod("dailyOfMonth", ZoneId.class, LocalDate.class, int.class,
                boolean.class, List.class);
        m.setAccessible(true);
        LocalDate first = LocalDate.of(2026, 2, 1); // fevereiro: 28 dias
        List<HourUsage> hours = List.of(
                new HourUsage(Instant.parse("2026-02-01T03:00:00Z"), 10, 0, 0, 500, 1), // dia 1 (cache read fora)
                new HourUsage(Instant.parse("2026-02-01T02:00:00Z"), 99, 0, 0, 0, 1),   // 31/01 23h local → clamp dia 1
                new HourUsage(Instant.parse("2026-02-28T20:00:00Z"), 5, 0, 0, 0, 1));   // dia 28
        List<Day> days = (List<Day>) m.invoke(null, SP, first, 28, false, hours);
        assertThat(days).hasSize(28);
        assertThat(days.get(0).tokens()).isEqualTo(109);
        assertThat(days.get(27).tokens()).isEqualTo(5);
        assertThat(days.get(27).cumulative()).isEqualTo(114);
        assertThat(days.get(1).date().toString()).isEqualTo("2026-02-02");
    }
}
