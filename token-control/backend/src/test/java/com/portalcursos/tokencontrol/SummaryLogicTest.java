package com.portalcursos.tokencontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Slice;
import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.repository.HourUsage;
import com.portalcursos.tokencontrol.repository.UsageAggregate;
import com.portalcursos.tokencontrol.service.CycleCalculator;
import com.portalcursos.tokencontrol.service.CycleWindow;
import com.portalcursos.tokencontrol.service.SummaryService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryLogicTest {

    private final PlanConfig cfg = new PlanConfig("Pro", 1, LocalTime.of(9, 0), "America/Sao_Paulo", 1000, false,
            Instant.EPOCH);
    private final CycleWindow window = CycleCalculator.cycleAt(cfg, Instant.parse("2026-10-01T12:00:00Z"));

    @SuppressWarnings("unchecked")
    private <T> T call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = SummaryService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return (T) m.invoke(null, args);
    }

    @Test
    void projecaoSemBaseEstatisticaRetornaNulo() throws Exception {
        Object p = call("projection", new Class[] {long.class, long.class, CycleWindow.class, Instant.class}, 10L,
                1000L, window, window.start().plusSeconds(1800));
        assertThat(p).isNull();
    }

    @Test
    void projecaoEstimaEstouroEHorarioDeEsgotamento() throws Exception {
        // 300 tokens em 1 dia de 7 -> projeção 2100 (>1000): estoura; esgota após ~ 2,33 dias
        Instant now = window.start().plusSeconds(86_400);
        Object p = call("projection", new Class[] {long.class, long.class, CycleWindow.class, Instant.class}, 300L,
                1000L, window, now);
        assertThat(p).hasFieldOrPropertyWithValue("projectedTotal", 2100L)
                .hasFieldOrPropertyWithValue("willExceed", true)
                .hasFieldOrPropertyWithValue("dailyAverage", 300L);
        Instant ex = (Instant) p.getClass().getMethod("exhaustionAt").invoke(p);
        assertThat(ex).isEqualTo(now.plusSeconds((long) (700 / (300.0 / 86_400))));
    }

    @Test
    void projecaoDentroDoLimiteNaoTemEsgotamento() throws Exception {
        Object p = call("projection", new Class[] {long.class, long.class, CycleWindow.class, Instant.class}, 100L,
                1000L, window, window.start().plusSeconds(86_400));
        assertThat(p).hasFieldOrPropertyWithValue("willExceed", false).hasFieldOrPropertyWithValue("exhaustionAt", null);
    }

    @Test
    void diarioAlinhaDiasAoHorarioDeResetEAcumula() throws Exception {
        List<HourUsage> hours = List.of(
                new HourUsage(window.start(), 10, 5, 0, 999, 1),                 // dia 1 (cache read ignorado)
                new HourUsage(window.start().plusSeconds(23 * 3600), 7, 0, 0, 0, 1), // ainda dia 1 (22:00 local)
                new HourUsage(window.start().plusSeconds(24 * 3600), 20, 0, 0, 0, 1)); // dia 2
        List<Day> days = call("daily", new Class[] {PlanConfig.class, CycleWindow.class, List.class}, cfg, window,
                hours);
        assertThat(days).hasSize(7);
        assertThat(days.get(0).tokens()).isEqualTo(22);
        assertThat(days.get(1).tokens()).isEqualTo(20);
        assertThat(days.get(6).cumulative()).isEqualTo(42);
        assertThat(days.get(0).date().toString()).isEqualTo("2026-09-28");
    }

    @Test
    void fatiasLimitamTopNeAgrupamOutros() throws Exception {
        List<UsageAggregate> rows = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            rows.add(new UsageAggregate("p" + i, i * 10L, 0, 0, 0, 1));
        }
        List<Slice> slices = call("slices", new Class[] {List.class, boolean.class}, rows, false);
        assertThat(slices).hasSize(13);
        assertThat(slices.get(0).name()).isEqualTo("p15");
        assertThat(slices.get(12).name()).isEqualTo("Outros");
        assertThat(slices.stream().mapToLong(Slice::tokens).sum()).isEqualTo(1200L);
    }
}
